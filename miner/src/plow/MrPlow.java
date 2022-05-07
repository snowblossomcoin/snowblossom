package snowblossom.miner.plow;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import duckutil.PeriodicThread;
import duckutil.TimeRecord;
import duckutil.jsonrpc.JsonRpcServer;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.client.WalletUtil;
import snowblossom.lib.*;
import snowblossom.lib.db.DB;
import snowblossom.lib.db.atomicfile.AtomicFileDB;
import snowblossom.lib.db.lobstack.LobstackDB;
import snowblossom.lib.db.rocksdb.JRocksDB;
import snowblossom.lib.tls.CertGen;
import snowblossom.mining.proto.*;
import snowblossom.proto.*;

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

  public static final long TEMPLATE_MAX_AGE = 100000L;

  public static ByteString BLOCK_KEY = ByteString.copyFrom(new String("blocks_found").getBytes());
  public static String PPLNS_STATE_KEY = "pplns_state";


  public static void main(String args[]) throws Exception
  {
    Globals.addCryptoProvider();
    if (args.length != 1)
    {
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: MrPlow <config_file>");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0],"snowblossom_");

    LogSetup.setup(config);

    MrPlow miner = new MrPlow(config);

  }

  private volatile Block last_block_template;

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

  private final PlowLoop loop;
  private List<NodeConnection> connections;
  private final Server grpc_server;
  private final Server grpc_server_tls;
  private AddressSpecHash tls_key_id;

  public MrPlow(Config config) throws Exception
  {
    this.config = config;
    logger.info(String.format("Starting MrPlow version %s", Globals.VERSION));

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
      pplns_state = PPLNSState.parseFrom(db.getSpecialMap().get(PPLNS_STATE_KEY));
      logger.info(String.format("Loaded PPLNS state with %d entries", pplns_state.getShareEntriesCount()));
    }
    catch(Throwable t)
    {
      logger.log(Level.WARNING, "Unable to load PPLNS state, starting fresh:" + t);
    }

    share_manager = new ShareManager(fixed_fee_map, pplns_state);
    report_manager = new ReportManager();

    startConnections();
    subscribe();

    grpc_server = ServerBuilder
      .forPort(port)
      .addService(agent)
      .build();
    grpc_server.start();

    if (config.isSet("tls_mining_pool_port"))
    {
      int tls_port = config.getInt("tls_mining_pool_port");
      config.require("tls_key_path");
      WalletDatabase wallet_db = WalletUtil.loadNodeWalletFromConfig(params, config, "tls_key_path");

      AddressSpecHash node_tls_address = AddressUtil.getHashForSpec(wallet_db.getAddresses(0));
      tls_key_id = node_tls_address;
      logger.info("My TLS address: " + AddressUtil.getAddressString(Globals.NODE_ADDRESS_STRING, node_tls_address));

      SslContext ssl_ctx = CertGen.getServerSSLContext(wallet_db);
      grpc_server_tls = NettyServerBuilder
        .forPort(tls_port)
        .addService(agent)
        .sslContext(ssl_ctx)
        .build();
      grpc_server_tls.start();

    }
    else
    {
      grpc_server_tls = null;
    }

    if (config.isSet("rpc_port"))
    {
      JsonRpcServer json_server = new JsonRpcServer(config, false);
      new MrPlowJsonHandler(this).registerHandlers(json_server);
    }

    loop = new PlowLoop();
    loop.start();
  }

  public String getTlsKeyId()
  {
    return AddressUtil.getAddressString(Globals.NODE_ADDRESS_STRING, tls_key_id);
  }


  public int getGrpcPort()
  {
    return grpc_server.getPort();
  }

  public int getGrpcTlsPort()
  {
    return grpc_server_tls.getPort();
  }


  public int getMinDiff()
  {
    return min_diff;
  }

  public int getMaxDiff()
  {
    BigInteger target = BlockchainUtil.targetBytesToBigInteger(getBlockTemplate().getHeader().getTarget());
    return ((int)PowUtil.getDiffForTarget(target)) - 1;
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
    else if (db_type.equals("atomic_file"))
    {
      db = new DB(config, new AtomicFileDB(config));
    }
    else
    {
      logger.log(Level.SEVERE, String.format("Unknown db_type: %s", db_type));
      throw new RuntimeException("Unable to load DB");
    }

    db.open();

  }

  public class PlowLoop extends PeriodicThread
  {
    private long last_report;
    public PlowLoop()
    {
      super(20000);
      last_report = System.currentTimeMillis();

      setDaemon(false);
      setName("PlowLoop");
    }

    @Override
    public void runPass() throws Exception
    {
      printStats();
      // Subscribe causes a new block template to be built from the
      // share manager so has to be run
      subscribe();
      prune();
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
    db.getSpecialMap().put(PPLNS_STATE_KEY, state.toByteString());
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
        logger.fine(String.format("Pruning to %d shares", shares_to_keep));
        share_manager.prune(shares_to_keep);
      }
  }

  private void startConnections()
  {
    config.require("node_uri");

    List<String> uri_list = config.getList("node_uri");
    LinkedList<NodeConnection> conns = new LinkedList<>();

    for(String uri : uri_list)
    {
      NodeConnection nc = new NodeConnection(this, uri, params);
      nc.start();
      conns.add(nc);
    }

    connections = ImmutableList.copyOf(conns);
  }

  public List<NodeConnection> getConnections()
  {
    return connections;
  }

  private void subscribe() throws Exception
  {
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

    Map<String, Double> rates = share_manager.getPayRatios();

    SubscribeBlockTemplateRequest req =
      SubscribeBlockTemplateRequest.newBuilder()
        .putAllPayRatios( rates )
        .setExtras(extras.build())
        .build();
    logger.info("Block template updated - " + rates);

    for(NodeConnection nc : connections)
    {
      nc.updateSubscription(req);
    }
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
    loop.halt();

  }

  private volatile boolean terminate = false;

  public NetworkParams getParams() {return params;}

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

  public void updateBlockTemplate()
  {
    TreeSet<BlockCompare> potential_blocks = new TreeSet<>();

    for(NodeConnection nc : connections)
    {
      BlockTemplate bt = nc.getLatestBlockTemplate();
      if (bt != null)
      if (bt.getBlock().getHeader().getTimestamp() + TEMPLATE_MAX_AGE > System.currentTimeMillis())
      {
        potential_blocks.add(new BlockCompare(bt));
      }
    }

    if (potential_blocks.size() == 0)
    {
      logger.warning("Selected no block template");
      last_block_template = null;
    }
    else
    {
      Block new_template = potential_blocks.first().getBlockTemplate().getBlock();

      if ((last_block_template == null) || (!new_template.equals(last_block_template)))
      {
        DecimalFormat df = new DecimalFormat("0.00000000");

        last_block_template = potential_blocks.first().getBlockTemplate().getBlock();
        logger.info(String.format("Selected block: s:%d h:%d %s",
          last_block_template.getHeader().getShardId(),
          last_block_template.getHeader().getBlockHeight(),
          df.format(potential_blocks.first().getRewardPerHash())));
        agent.updateBlockTemplate(last_block_template);
      }
    }

  }

  public void submitBlock(Block blk)
  {
    for(NodeConnection nc : connections)
    {
      nc.submitBlock(blk, new SubmitReporter());
    }
  }

  public class SubmitReporter implements StreamObserver<SubmitReply>
  {
    @Override
    public void onCompleted()
    {

    }

    @Override
    public void onError(Throwable t)
    {
      logger.warning("Block submit error: " + t);
    }

    @Override
    public void onNext(SubmitReply reply)
    {
      logger.info("Block submit reply: " + reply);
    }


  }
}
