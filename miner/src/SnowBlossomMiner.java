package snowblossom.miner;

import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import duckutil.MultiAtomicLong;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;

public class SnowBlossomMiner
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  public static void main(String args[]) throws Exception
  {
    Globals.addCryptoProvider();
    if (args.length != 1)
    {
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: SnowBlossomMiner <config_file>");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0]);

    LogSetup.setup(config);


    SnowBlossomMiner miner = new SnowBlossomMiner(config);

    while (true)
    {
      Thread.sleep(15000);
      miner.printStats();
    }
  }

  private volatile Block last_block_template;

  private UserServiceStub asyncStub;
  private UserServiceBlockingStub blockingStub;

  private final FieldScan field_scan;
  private final NetworkParams params;

  private MultiAtomicLong op_count = new MultiAtomicLong();
  private long last_stats_time = System.currentTimeMillis();
  private Config config;

  private File snow_path;

  private TimeRecord time_record;


  public SnowBlossomMiner(Config config) throws Exception
  {
    this.config = config;
    logger.info(String.format("Starting SnowBlossomMiner version %s", Globals.VERSION));

    config.require("snow_path");
    config.require("node_host");

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

    String host = config.get("node_host");
    int port = config.getIntWithDefault("node_port", params.getDefaultPort());
    channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();

    asyncStub = UserServiceGrpc.newStub(channel);
    blockingStub = UserServiceGrpc.newBlockingStub(channel);

    AddressSpecHash to_addr = getMineToAddress();

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



    asyncStub.subscribeBlockTemplate(SubscribeBlockTemplateRequest.newBuilder().setPayRewardToSpecHash(to_addr.getBytes()).setExtras(extras.build()).build(),
                                     new BlockTemplateEater());
    logger.info("Subscribed to blocks");

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
      WalletDatabase wallet = WalletUtil.loadWallet(wallet_path, false, params);

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
    double count = op_count.sumAndReset();

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

  public Block getBlockTemplate()
  {
    return last_block_template;
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
      Block b = last_block_template;
      if (b == null)
      {
        try (TimeRecordAuto tra = TimeRecord.openAuto("MinerThread.nullBlockSleep"))
        {
          Thread.sleep(100);
          return;
        }
      }
      if (b.getHeader().getTimestamp() + 75000 < System.currentTimeMillis())
      {
        logger.log(Level.WARNING, "Last block is old, not mining it");
        last_block_template = null;
      }

      try (TimeRecordAuto tra = TimeRecord.openAuto("MinerThread.rndNonce"))
      {
        rnd.nextBytes(nonce);
      }

      // TODO, modify headers to put snow field in
      byte[] first_hash = PowUtil.hashHeaderBits(b.getHeader(), nonce, md);


      /**
       * This is a windows specific improvement since windows likes separete file descriptors
       *  per thread.
       */
      if ((merkle_proof == null) || (proof_field != b.getHeader().getSnowField()))
      {
        merkle_proof = field_scan.getSingleUserFieldProof(b.getHeader().getSnowField());
        proof_field = b.getHeader().getSnowField();
      }

      byte[] context = first_hash;

      try (TimeRecordAuto tra = null)
      {
        for (int pass = 0; pass < Globals.POW_LOOK_PASSES; pass++)
        {
          long word_idx;
          ((Buffer)word_bb).clear();
          word_idx = PowUtil.getNextSnowFieldIndex(context, merkle_proof.getTotalWords(), md);
          merkle_proof.readWord(word_idx, word_bb, pass);
          context = PowUtil.getNextContext(context, word_buff, md);
        }
      }


      byte[] found_hash = context;

      if (PowUtil.lessThanTarget(found_hash, b.getHeader().getTarget()))
      {
        String str = HashUtils.getHexString(found_hash);
        logger.info("Found passable solution: " + str);
        buildBlock(b, nonce, merkle_proof);

      }
      op_count.add(1L);
    }

    private void buildBlock(Block b, byte[] nonce, SnowMerkleProof merkle_proof) throws Exception
    {
      Block.Builder bb = Block.newBuilder().mergeFrom(b);

      BlockHeader.Builder header = BlockHeader.newBuilder().mergeFrom(b.getHeader());
      header.setNonce(ByteString.copyFrom(nonce));

      byte[] first_hash = PowUtil.hashHeaderBits(b.getHeader(), nonce);
      byte[] context = first_hash;

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

      bb.setHeader(header);

      Block new_block = bb.build();
      //logger.info("New block: " + new_block);
      SubmitReply reply = blockingStub.submitBlock(new_block);
      logger.info("Block submit: " + reply);

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

  public class BlockTemplateEater implements StreamObserver<Block>
  {
    public void onCompleted() {}

    public void onError(Throwable t) {}

    public void onNext(Block b)
    {
      logger.finer("Got block template: height:" + b.getHeader().getBlockHeight() + " transactions:" + b.getTransactionsCount());


      int min_field = b.getHeader().getSnowField();


      logger.finer("Required field: " + min_field + " - " + params.getSnowFieldInfo(min_field).getName());

      int selected_field = -1;

      try
      {
        selected_field = field_scan.selectField(min_field);
        logger.finer("Using field: " + selected_field + " - " + params.getSnowFieldInfo(selected_field).getName());

        try
        {
          field_scan.selectField(min_field + 1);
        }
        catch (Throwable t)
        {
          logger.log(Level.WARNING, "When the next snow storm occurs, we will be unable to mine.  No higher fields working.");
        }

        // write selected field into block template 
        Block.Builder bb = Block.newBuilder();
        bb.mergeFrom(b);

        BlockHeader.Builder bh = BlockHeader.newBuilder();
        bh.mergeFrom(b.getHeader());
        bh.setSnowField(selected_field);
        bb.setHeader(bh.build());

        last_block_template = bb.build();
      }
      catch (Throwable t)
      {
        logger.info("Work block load error: " + t.toString());
        last_block_template = null;
      }
    }
  }
}
