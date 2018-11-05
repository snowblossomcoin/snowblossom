package snowblossom.miner;

import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import duckutil.TimeRecord;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.mining.proto.*;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import snowblossom.lib.db.DB;
import snowblossom.lib.db.lobstack.LobstackDB;
import snowblossom.lib.db.rocksdb.JRocksDB;

import io.grpc.Server;
import io.grpc.ServerBuilder;


import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.TreeMap;
import java.util.List;
import duckutil.jsonrpc.JsonRpcServer;

public class MrPlow
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");
  
  public static final int BACK_BLOCKS=5; // Roughly how many blocks back to keep shares for PPLNS

  // Basically read this as if there are SHARES_IN_VIEW_FOR_RETARGET
  // inside a single SHARE_VIEW_WINDOW, then move the miner up one difficulty
  // So as it is set, if a miner gets 12 shares inside of 2 minutes, move them up.
  public static final long SHARE_VIEW_WINDOW = 120000L;
  public static final int SHARES_IN_VIEW_FOR_UPTARGET = 12;
  public static final int SHARES_IN_VIEW_FOR_DOWNTARGET = 4;

  public static ByteString BLOCK_KEY = ByteString.copyFrom(new String("blocks_found").getBytes());


  public static void main(String args[]) throws Exception
  {
    Globals.addCryptoProvider();
    if (args.length != 1)
    {
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: MrPlow <config_file>");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0]);

    LogSetup.setup(config);

    MrPlow miner = new MrPlow(config);

    miner.loop();
  }

  private volatile Block last_block_template;

  private UserServiceStub asyncStub;
  private UserServiceBlockingStub blockingStub;

  private final NetworkParams params;

  private AtomicLong op_count = new AtomicLong(0L);
  private long last_stats_time = System.currentTimeMillis();
  private Config config;

  private TimeRecord time_record;
  private MiningPoolServiceAgent agent;

  private ShareManager share_manager;
	private DB db;
  private ReportManager report_manager;
  private final int min_diff;

  public MrPlow(Config config) throws Exception
  {
    this.config = config;
    logger.info(String.format("Starting MrPlow version %s", Globals.VERSION));

    config.require("node_host");
    config.require("pool_address");
    config.require("pool_fee");
    config.require("db_type");
    config.require("db_path");
    min_diff = config.getIntWithDefault("min_diff", 22);
    
    params = NetworkParams.loadFromConfig(config);

    if (config.getBoolean("display_timerecord"))
    {
      time_record = new TimeRecord();
      TimeRecord.setSharedRecord(time_record);
    }

    

    int port = config.getIntWithDefault("mining_pool_port",23380);
    agent = new MiningPoolServiceAgent(this);

    double pool_fee = config.getDouble("pool_fee");
    double duck_fee = config.getDoubleWithDefault("pay_the_duck", 0.0);

    TreeMap<String, Double> fixed_fee_map = new TreeMap<>();
    fixed_fee_map.put( AddressUtil.getAddressString(params.getAddressPrefix(), getPoolAddress()), pool_fee );
    if (duck_fee > 0.0)
    {
      fixed_fee_map.put( "snow:crqls8qkumwg353sfgf5kw2lw2snpmhy450nqezr", duck_fee);
    }
		loadDB();

		PPLNSState pplns_state = null;
		try
		{
      pplns_state = PPLNSState.parseFrom(db.getSpecialMap().get("pplns_state"));
      logger.info(String.format("Loaded PPLNS state with %d entries", pplns_state.getShareEntriesCount()));
		}
    catch(Throwable t)
    {
      logger.log(Level.WARNING, "Unable to load PPLNS state, starting fresh:" + t);
    }

    share_manager = new ShareManager(fixed_fee_map, pplns_state);
    report_manager = new ReportManager();

    subscribe();

    Server s = ServerBuilder
      .forPort(port)
      .addService(agent)
      .build();

    if (config.isSet("rpc_port"))
    {
      JsonRpcServer json_server = new JsonRpcServer(config, false);
      new MrPlowJsonHandler(this).registerHandlers(json_server);

    }

    s.start();
  }

  public int getMinDiff()
  {
    return min_diff;
  }

  private void loadDB()
    throws Exception
  {
    String db_type = config.get("db_type");

    if(db_type.equals("rocksdb"))
    {
      db = new DB(config, new JRocksDB(config));
    }
    else if (db_type.equals("lobstack"))
    {
      db = new DB(config, new LobstackDB(config));
    }
    else
    {
      logger.log(Level.SEVERE, String.format("Unknown db_type: %s", db_type));
      throw new RuntimeException("Unable to load DB");
    }

    db.open();

  }

  private void loop()
    throws Exception
  {
    long last_report = System.currentTimeMillis();

    while (true)
    {
      Thread.sleep(20000);
      printStats();
      prune();
      subscribe();
      saveState();

      if (config.isSet("report_path"))
      {
      if (last_report + 60000L < System.currentTimeMillis())
      {
        report_manager.writeReport(config.get("report_path"));

        last_report = System.currentTimeMillis();
      }
      }
     

    }
  }

  public DB getDB() {return db;}

  private void saveState()
  {
    PPLNSState state = share_manager.getState();
    db.getSpecialMap().put("pplns_state", state.toByteString());
  }
  private ManagedChannel channel;

  public void recordHashes(long n)
  {
    op_count.addAndGet(n);
  }
  private void prune()
  {
      Block b = last_block_template;
      if (b!=null)
      {
        double diff_delta = PowUtil.getDiffForTarget(BlockchainUtil.targetBytesToBigInteger(b.getHeader().getTarget())) - getMinDiff();

        long shares_to_keep = Math.round( Math.pow(2, diff_delta) * BACK_BLOCKS);
        share_manager.prune(shares_to_keep);
      }
  }
  private void subscribe() throws Exception
  {
    if (channel != null)
    {
      channel.shutdownNow();
      channel = null;
    }

    String host = config.get("node_host");
    int port = config.getIntWithDefault("node_port", params.getDefaultPort());
    channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();

    asyncStub = UserServiceGrpc.newStub(channel);
    blockingStub = UserServiceGrpc.newBlockingStub(channel);

    CoinbaseExtras.Builder extras = CoinbaseExtras.newBuilder();
    if (config.isSet("remark"))
    {
      extras.setRemarks(ByteString.copyFrom(config.get("remark").getBytes()));
    }
    if (config.isSet("vote_yes"))
    {
      List<String> lst = config.getList("vote_yes");
      for(String s : lst)
      {
        extras.addMotionsApproved( Integer.parseInt(s));
      }
    }
    if (config.isSet("vote_no"))
    {
      List<String> lst = config.getList("vote_no");
      for(String s : lst)
      {
        extras.addMotionsRejected( Integer.parseInt(s));
      }
    }



    asyncStub.subscribeBlockTemplate(
      SubscribeBlockTemplateRequest.newBuilder()
        .putAllPayRatios( share_manager.getPayRatios() )
        .setExtras(extras.build()).build(),
                                     new BlockTemplateEater());
    logger.info("Subscribed to blocks");

  }

  private AddressSpecHash getPoolAddress() throws Exception
  {
      String address = config.get("pool_address");
      AddressSpecHash to_addr = new AddressSpecHash(address, params);
      return to_addr;
  }

  public void stop()
  {
    terminate = true;
  }

  private volatile boolean terminate = false;

  public NetworkParams getParams() {return params;}

  public UserServiceBlockingStub getBlockingStub(){return blockingStub;}
  public ShareManager getShareManager(){return share_manager;}
  public ReportManager getReportManager(){return report_manager;}
  public MiningPoolServiceAgent getAgent(){return agent;}

  public void printStats()
  {
    long now = System.currentTimeMillis();
    double count = op_count.getAndSet(0L);

    double time_ms = now - last_stats_time;
    double time_sec = time_ms / 1000.0;
    double rate = count / time_sec;

    DecimalFormat df = new DecimalFormat("0.000");

    String block_time_report = "";
    if (last_block_template != null)
    {
      BigInteger target = BlockchainUtil.targetBytesToBigInteger(last_block_template.getHeader().getTarget());

      double diff = PowUtil.getDiffForTarget(target);

      double block_time_sec = Math.pow(2.0, diff) / rate;
      double hours = block_time_sec / 3600.0;
      block_time_report = String.format("- at this rate %s hours per block", df.format(hours));
    }

    logger.info(String.format("Mining rate: %s", report_manager.getTotalRate().getReportLong(df)));

    logger.info(String.format("Mining rate: %s/sec %s", df.format(rate), block_time_report));

    last_stats_time = now;

    if (config.getBoolean("display_timerecord"))
    {

      TimeRecord old = time_record;

      time_record = new TimeRecord();
      TimeRecord.setSharedRecord(time_record);

      old.printReport(System.out);

    }
  }

  public Block getBlockTemplate()
  {
    return last_block_template;
  }



  public class BlockTemplateEater implements StreamObserver<Block>
  {
    public void onCompleted() {}

    public void onError(Throwable t) {}

    public void onNext(Block b)
    {
      logger.info("Got block template: height:" + b.getHeader().getBlockHeight() + " transactions:" + b.getTransactionsCount());

      last_block_template = b;
      agent.updateBlockTemplate(b);
    }
  }
}
