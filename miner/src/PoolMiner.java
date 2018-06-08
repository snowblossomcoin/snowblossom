package snowblossom.miner;

import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.mining.proto.*;
import snowblossom.mining.proto.MiningPoolServiceGrpc.MiningPoolServiceStub;
import snowblossom.mining.proto.MiningPoolServiceGrpc.MiningPoolServiceBlockingStub;
import snowblossom.lib.trie.HashUtils;
import snowblossom.lib.SnowMerkleProof;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PoolMiner
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  public static void main(String args[]) throws Exception
  {
    Globals.addCryptoProvider();
    if (args.length != 1)
    {
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: PoolMiner <config_file>");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0]);

    LogSetup.setup(config);

    PoolMiner miner = new PoolMiner(config);

    while (true)
    {
      Thread.sleep(15000);
      miner.printStats();
    }
  }

  private volatile WorkUnit last_work_unit;

  private MiningPoolServiceStub asyncStub;
  private MiningPoolServiceBlockingStub blockingStub;

  private final FieldScan field_scan;
  private final NetworkParams params;

  private AtomicLong op_count = new AtomicLong(0L);
  private long last_stats_time = System.currentTimeMillis();
  private Config config;

  private File snow_path;

  private TimeRecord time_record;

  public PoolMiner(Config config) throws Exception
  {
    this.config = config;
    logger.info(String.format("Starting PoolMiner version %s", Globals.VERSION));

    config.require("snow_path");
    config.require("pool_host");

    params = NetworkParams.loadFromConfig(config);

    snow_path = new File(config.get("snow_path"));

    if ((!config.isSet("mine_to_address")) && (!config.isSet("mine_to_wallet")))
    {
      throw new RuntimeException("Config must either specify mine_to_address or mine_to_wallet");
    }
    if ((config.isSet("mine_to_address")) && (config.isSet("mine_to_wallet")))
    {
      throw new RuntimeException("Config must either specify mine_to_address or mine_to_wallet, not both");
    }
    if (config.getBoolean("display_timerecord"))
    {
      time_record = new TimeRecord();
      TimeRecord.setSharedRecord(time_record);
    }

    int threads = config.getIntWithDefault("threads", 8);
    logger.info("Starting " + threads + " threads");

    field_scan = new FieldScan(snow_path, params, config);
    subscribe();

    for (int i = 0; i < threads; i++)
    {
      new MinerThread().start();
    }
    //new Sweeper(this).start();
  }

  private ManagedChannel channel;

  private void subscribe() throws Exception
  {
    if (channel != null)
    {
      channel.shutdownNow();
      channel = null;
    }

    String host = config.get("pool_host");
    int port = config.getIntWithDefault("pool_port", 23380);
    channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();

    asyncStub = MiningPoolServiceGrpc.newStub(channel);
    blockingStub = MiningPoolServiceGrpc.newBlockingStub(channel);

    AddressSpecHash to_addr = getMineToAddress();
    String address_str = AddressUtil.getAddressString(params.getAddressPrefix(), to_addr);

    String client_id = null;
    if (config.isSet("mining_client_id"))
    {
      client_id = config.get("mining_client_id");
    }
    GetWorkRequest.Builder req = GetWorkRequest.newBuilder();
    if (client_id != null) req.setClientId(client_id);

    req.setPayToAddress(address_str);

    asyncStub.getWork( req.build(), new WorkUnitEater());
    logger.info("Subscribed to work");

  }

  private AddressSpecHash getMineToAddress() throws Exception
  {

    if (config.isSet("mine_to_address"))
    {
      String address = config.get("mine_to_address");
      AddressSpecHash to_addr = new AddressSpecHash(address, params);
      return to_addr;
    }
    if (config.isSet("mine_to_wallet"))
    {
      File wallet_path = new File(config.get("mine_to_wallet"));
      File wallet_db = new File(wallet_path, "wallet.db");

      FileInputStream in = new FileInputStream(wallet_db);
      WalletDatabase wallet = WalletDatabase.parseFrom(in);
      in.close();
      if (wallet.getAddressesCount() == 0)
      {
        throw new RuntimeException("Wallet has no addresses");
      }
      LinkedList<AddressSpec> specs = new LinkedList<AddressSpec>();
      specs.addAll(wallet.getAddressesList());
      Collections.shuffle(specs);

      AddressSpec spec = specs.get(0);
      AddressSpecHash to_addr = AddressUtil.getHashForSpec(spec);
      return to_addr;
    }
    return null;
  }

  public void stop()
  {
    terminate = true;
  }

  private volatile boolean terminate = false;

  public void printStats()
  {
    long now = System.currentTimeMillis();
    double count = op_count.getAndSet(0L);

    double time_ms = now - last_stats_time;
    double time_sec = time_ms / 1000.0;
    double rate = count / time_sec;

    DecimalFormat df = new DecimalFormat("0.000");

    String block_time_report = "";
    if (last_work_unit != null)
    {
      BigInteger target = BlockchainUtil.targetBytesToBigInteger(last_work_unit.getReportTarget());

      double diff = PowUtil.getDiffForTarget(target);

      double block_time_sec = Math.pow(2.0, diff) / rate;
      double min = block_time_sec / 60.0;
      block_time_report = String.format("- at this rate %s minutes per share", df.format(min));
    }


    logger.info(String.format("Mining rate: %s/sec %s", df.format(rate), block_time_report));

    last_stats_time = now;

    if (count == 0)
    {
      logger.info("we seem to be stalled, reconnecting to node");
      try
      {
        subscribe();
      }
      catch (Throwable t)
      {
        logger.info("Exception in subscribe: " + t);
      }
    }

    if (config.getBoolean("display_timerecord"))
    {

      TimeRecord old = time_record;

      time_record = new TimeRecord();
      TimeRecord.setSharedRecord(time_record);

      old.printReport(System.out);

    }
  }

  public WorkUnit getWorkUnit()
  {
    return last_work_unit;
  }

  public FieldScan getFieldScan()
  {
    return field_scan;
  }

  public class MinerThread extends Thread
  {
    Random rnd;
    MessageDigest md = DigestUtil.getMD();

    byte[] word_buff = new byte[SnowMerkle.HASH_LEN];
    ByteBuffer word_bb = ByteBuffer.wrap(word_buff);
    SnowMerkleProof merkle_proof;
    int proof_field;
    byte[] nonce = new byte[Globals.NONCE_LENGTH];

    public MinerThread()
    {
      setName("MinerThread");
      setDaemon(true);
      rnd = new Random();

    }

    private void runPass() throws Exception
    {
      WorkUnit wu = last_work_unit;
      if (wu == null)
      {
        try (TimeRecordAuto tra = TimeRecord.openAuto("MinerThread.nullBlockSleep"))
        {
          Thread.sleep(100);
          return;
        }
      }
      if (wu.getHeader().getTimestamp() + 75000 < System.currentTimeMillis())
      {
        logger.log(Level.WARNING, "Work Unit is old, not mining it");
        last_work_unit = null;
      }
      

      try (TimeRecordAuto tra = TimeRecord.openAuto("MinerThread.rndNonce"))
      {
        rnd.nextBytes(nonce);
        wu.getHeader().getNonce().copyTo(nonce, 0);
      }

      byte[] first_hash = PowUtil.hashHeaderBits(wu.getHeader(), nonce, md);

      /**
       * This is a windows specific improvement since windows likes separete file descriptors
       *  per thread.
       */
      if ((merkle_proof == null) || (proof_field != wu.getHeader().getSnowField()))
      {
        merkle_proof = field_scan.getSingleUserFieldProof(wu.getHeader().getSnowField());
        proof_field = wu.getHeader().getSnowField();
      }

      byte[] context = first_hash;

      try (TimeRecordAuto tra = null)
      {
        for (int pass = 0; pass < Globals.POW_LOOK_PASSES; pass++)
        {
          long word_idx;
          word_bb.clear();
          word_idx = PowUtil.getNextSnowFieldIndex(context, merkle_proof.getTotalWords(), md);
          merkle_proof.readWord(word_idx, word_bb);
          context = PowUtil.getNextContext(context, word_buff, md);
        }
      }

      byte[] found_hash = context;

      if (PowUtil.lessThanTarget(found_hash, wu.getReportTarget()))
      {
        String str = HashUtils.getHexString(found_hash);
        logger.info("Found passable solution: " + str);
        submitWork(wu, nonce, merkle_proof);

      }
      op_count.getAndIncrement();
    }

    private void submitWork(WorkUnit wu, byte[] nonce, SnowMerkleProof merkle_proof) throws Exception
    {
      byte[] first_hash = PowUtil.hashHeaderBits(wu.getHeader(), nonce);
      byte[] context = first_hash;


      BlockHeader.Builder header = BlockHeader.newBuilder();
      header.mergeFrom(wu.getHeader());
      header.setNonce(ByteString.copyFrom(nonce));


      for (int pass = 0; pass < Globals.POW_LOOK_PASSES; pass++)
      {
        word_bb.clear();

        long word_idx = PowUtil.getNextSnowFieldIndex(context, merkle_proof.getTotalWords());
        merkle_proof.readWord(word_idx, word_bb);
        SnowPowProof proof = merkle_proof.getProof(word_idx);
        header.addPowProof(proof);
        context = PowUtil.getNextContext(context, word_buff);
      }

      byte[] found_hash = context;

      header.setSnowHash(ByteString.copyFrom(found_hash));

      WorkSubmitRequest.Builder req = WorkSubmitRequest.newBuilder();
      req.setWorkId(wu.getWorkId());
      req.setHeader(header.build());
      
      SubmitReply reply = blockingStub.submitWork( req.build());
      
      logger.info("Work submit: " + reply);

    }


    public void run()
    {
      while (!terminate)
      {
        boolean err = false;
        try (TimeRecordAuto tra = TimeRecord.openAuto("MinerThread.runPass"))
        {
          runPass();
        }
        catch (Throwable t)
        {
          err = true;
          logger.warning("Error: " + t);
        }

        if (err)
        {

          try (TimeRecordAuto tra = TimeRecord.openAuto("MinerThread.errorSleep"))
          {
            Thread.sleep(5000);
          }
          catch (Throwable t)
          {
          }
        }
      }
    }
  }

  public class WorkUnitEater implements StreamObserver<WorkUnit>
  {
    public void onCompleted() {}

    public void onError(Throwable t) {}

    public void onNext(WorkUnit wu)
    {

      int min_field = wu.getHeader().getSnowField();

      int selected_field = -1;

      try
      {
        selected_field = field_scan.selectField(min_field);

        try
        {
          field_scan.selectField(min_field + 1);
        }
        catch (Throwable t)
        {
          logger.log(Level.WARNING, "When the next snow storm occurs, we will be unable to mine.  No higher fields working.");
        }

        // write selected field into block template 

        BlockHeader.Builder bh = BlockHeader.newBuilder();
        bh.mergeFrom(wu.getHeader());
        bh.setSnowField(selected_field);

        WorkUnit wu_new = WorkUnit.newBuilder()
          .mergeFrom(wu)
          .setHeader(bh.build())
          .build();

        last_work_unit = wu_new;
      }
      catch (Throwable t)
      {
        logger.info("Work block load error: " + t.toString());
        last_work_unit = null;
      }
    }
  }
}
