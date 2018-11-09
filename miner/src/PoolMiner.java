package snowblossom.miner;

import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import duckutil.RateReporter;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.mining.proto.*;
import snowblossom.mining.proto.MiningPoolServiceGrpc.MiningPoolServiceStub;
import snowblossom.mining.proto.MiningPoolServiceGrpc.MiningPoolServiceBlockingStub;
import snowblossom.lib.trie.HashUtils;
import snowblossom.client.WalletUtil;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.Buffer;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import duckutil.MultiAtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PoolMiner implements PoolClientOperator
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

  private final FieldScan field_scan;
  private final NetworkParams params;

  private MultiAtomicLong op_count = new MultiAtomicLong();
  private long last_stats_time = System.currentTimeMillis();
  private Config config;

  private File snow_path;

  private TimeRecord time_record;
  private RateReporter rate_report=new RateReporter();

  private AtomicLong share_submit_count = new AtomicLong(0L);
  private AtomicLong share_reject_count = new AtomicLong(0L);
  private AtomicLong share_block_count = new AtomicLong(0L);

  private PoolClientFace pool_client;

  public PoolMiner(Config config) throws Exception
  {
    this.config = config;
    logger.info(String.format("Starting PoolMiner version %s", Globals.VERSION));

    config.require("snow_path");

    params = NetworkParams.loadFromConfig(config);

    if (config.isSet("pool_host_list"))
    {
      pool_client = new PoolClientFailover(config, this);
    }
    else
    {
      pool_client = new PoolClient(config, this);
    }

    snow_path = new File(config.get("snow_path"));
    
    field_scan = new FieldScan(snow_path, params, config);

    if (config.getBoolean("display_timerecord"))
    {
      time_record = new TimeRecord();
      TimeRecord.setSharedRecord(time_record);
    }

    pool_client.subscribe();

    int threads = config.getIntWithDefault("threads", 8);
    logger.info("Starting " + threads + " threads");


    for (int i = 0; i < threads; i++)
    {
      new MinerThread().start();
    }
  }

  private ManagedChannel channel;

  public void stop()
  {
    terminate = true;
    pool_client.stop();
  }

  private volatile boolean terminate = false;

  public void printStats()
  {
    long now = System.currentTimeMillis();
    long count_long = op_count.sumAndReset();
    double count = count_long;
    rate_report.record(count_long);

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
      block_time_report = String.format("- at this rate %s minutes per share (diff %s)", df.format(min), df.format(diff));
    }


    logger.info(String.format("15 Second mining rate: %s/sec %s", df.format(rate), block_time_report));
    logger.info(rate_report.getReportShort(df));

    last_stats_time = now;

    if (count == 0)
    {
      if (getWorkUnit() == null)
      {

        logger.info("Stalled.  No valid work unit, reconnecting to pool");
        try
        {
          pool_client.subscribe();
        }
        catch (Throwable t)
        {
          logger.info("Exception in subscribe: " + t);
        }
      }
      else
      {
        logger.info("No hashing, and we have a good work unit from the pool.  So probably something else wrong.");
        logger.info("Probably code EBCAK");
      }
    }



    if (config.getBoolean("display_timerecord"))
    {

      TimeRecord old = time_record;

      time_record = new TimeRecord();
      TimeRecord.setSharedRecord(time_record);

      old.printReport(System.out);
    }

    logger.info(String.format("Shares: %d (rejected %d) (blocks %d)", share_submit_count.get(), share_reject_count.get(), share_block_count.get()));
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
          ((Buffer)word_bb).clear();
          word_idx = PowUtil.getNextSnowFieldIndex(context, merkle_proof.getTotalWords(), md);
          if (!merkle_proof.readWord(word_idx, word_bb, pass)) { return;}
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
      op_count.add(1L);
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
        ((Buffer)word_bb).clear();

        long word_idx = PowUtil.getNextSnowFieldIndex(context, merkle_proof.getTotalWords());
        boolean gotData = merkle_proof.readWord(word_idx, word_bb, pass);
        if (!gotData)
        {
          logger.log(Level.SEVERE, "readWord returned false on pass " + pass);
        }
        SnowPowProof proof = merkle_proof.getProof(word_idx);
        header.addPowProof(proof);
        context = PowUtil.getNextContext(context, word_buff);
      }

      byte[] found_hash = context;

      header.setSnowHash(ByteString.copyFrom(found_hash));

      SubmitReply reply = pool_client.submitWork(wu, header.build());
      
      if (PowUtil.lessThanTarget(found_hash, header.getTarget()))
      {
        share_block_count.getAndIncrement();
      }
      logger.info("Work submit: " + reply);
      share_submit_count.getAndIncrement();
      if (!reply.getSuccess())
      {
        share_reject_count.getAndIncrement();
      }

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

  @Override
  public void notifyNewBlock(int block_id){}

  @Override
  public void notifyNewWorkUnit(WorkUnit wu)
  {

    int min_field = wu.getHeader().getSnowField();

    int selected_field = -1;

    try
    {
      selected_field = field_scan.selectField(min_field);

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
