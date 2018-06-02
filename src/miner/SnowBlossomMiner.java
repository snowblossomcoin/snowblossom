package snowblossom.miner;

import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import snowblossom.proto.*;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import snowblossomlib.trie.HashUtils;
import snowblossomlib.SnowMerkleProof;

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

public class SnowBlossomMiner
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  public static void main(String args[]) throws Exception
  {
    snowblossomlib.Globals.addCryptoProvider();
    if (args.length != 1)
    {
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: SnowBlossomMiner <config_file>");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0]);
    
    snowblossomlib.LogSetup.setup(config);


    SnowBlossomMiner miner = new SnowBlossomMiner(config); 
    
    while(true)
    {
      Thread.sleep(15000);
      miner.printStats();
    }
  }

  private volatile Block last_block_template;

  private UserServiceStub asyncStub;
  private UserServiceBlockingStub blockingStub;

  private final FieldScan field_scan;
	private final snowblossomlib.NetworkParams params;

  private AtomicLong op_count = new AtomicLong(0L);
  private long last_stats_time = System.currentTimeMillis();
  private Config config;

  private File snow_path;

  private TimeRecord time_record;


  public SnowBlossomMiner(Config config) throws Exception
  {
    this.config = config;

    config.require("snow_path");
    config.require("node_host");
    
    params = snowblossomlib.NetworkParams.loadFromConfig(config);

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

    for(int i=0; i<threads; i++)
    {
      new MinerThread().start();
    }
    //new Sweeper(this).start();
  }

  private ManagedChannel channel;

  private void subscribe()
    throws Exception
  {
    if (channel != null)
    {
      channel.shutdownNow();
      channel=null;
    }

    String host = config.get("node_host");
    int port = config.getIntWithDefault("node_port", params.getDefaultPort());
    channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();

    asyncStub = UserServiceGrpc.newStub(channel);
    blockingStub = UserServiceGrpc.newBlockingStub(channel);

    snowblossomlib.AddressSpecHash to_addr = getMineToAddress();

    CoinbaseExtras.Builder extras = CoinbaseExtras.newBuilder();
    if (config.isSet("remark"))
    {
      extras.setRemarks(ByteString.copyFrom(config.get("remark").getBytes()));
    }

    asyncStub.subscribeBlockTemplate(
      SubscribeBlockTemplateRequest.newBuilder()
        .setPayRewardToSpecHash(to_addr.getBytes())
        .setExtras(extras.build())
        .build(), 
        new BlockTemplateEater());
    logger.info("Subscribed to blocks");  

  }

  private snowblossomlib.AddressSpecHash getMineToAddress()
    throws Exception
  {

    if (config.isSet("mine_to_address"))
    {
      String address = config.get("mine_to_address");
      snowblossomlib.AddressSpecHash to_addr = new snowblossomlib.AddressSpecHash(address, params);
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
      snowblossomlib.AddressSpecHash to_addr = snowblossomlib.AddressUtil.getHashForSpec(spec);
      return to_addr;
    }
    return null;
  }

  public void stop()
  {
    terminate=true;
  }
  private volatile boolean terminate=false;

  public void printStats()
  {
    long now = System.currentTimeMillis();
    double count = op_count.getAndSet(0L);

    double time_ms = now - last_stats_time;
    double time_sec = time_ms / 1000.0;
    double rate = count / time_sec;

    DecimalFormat df=new DecimalFormat("0.000");

    String block_time_report ="";
    if (last_block_template != null)
    {
      BigInteger target = snowblossomlib.BlockchainUtil.targetBytesToBigInteger(last_block_template.getHeader().getTarget());

      double diff = snowblossomlib.PowUtil.getDiffForTarget(target);

      double block_time_sec = Math.pow(2.0, diff) / rate;
      double hours = block_time_sec / 3600.0;
      block_time_report = String.format("- at this rate %s hours per block", df.format(hours));
    }


    logger.info(String.format("Mining rate: %s/sec %s", df.format(rate),block_time_report));

    last_stats_time = now;

    if (count == 0)
    {
      logger.info("we seem to be stalled, reconnecting to node");
      try
      {
        subscribe();
      }
      catch(Throwable t)
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
    MessageDigest md = snowblossomlib.DigestUtil.getMD();

    byte[] word_buff = new byte[snowblossomlib.SnowMerkle.HASH_LEN];
    ByteBuffer word_bb = ByteBuffer.wrap(word_buff);
    snowblossomlib.SnowMerkleProof merkle_proof;
    int proof_field;
    byte[] nonce = new byte[snowblossomlib.Globals.NONCE_LENGTH];

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
        try(TimeRecordAuto tra = TimeRecord.openAuto("MinerThread.nullBlockSleep"))
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

      try(TimeRecordAuto tra = TimeRecord.openAuto("MinerThread.rndNonce"))
      {
        rnd.nextBytes(nonce);
      }

      // TODO, modify headers to put snow field in
      byte[] first_hash = snowblossomlib.PowUtil.hashHeaderBits(b.getHeader(), nonce, md);

     
      /**
       * This is a windows specific improvement since windows likes separete file descriptors
       * per thread.
       */
      if ((merkle_proof == null) || (proof_field != b.getHeader().getSnowField()))
      {
        merkle_proof = field_scan.getSingleUserFieldProof(b.getHeader().getSnowField());
        proof_field = b.getHeader().getSnowField();
      }

      byte[] context = first_hash;

      try(TimeRecordAuto tra = null)
      {
        for(int pass = 0; pass< snowblossomlib.Globals.POW_LOOK_PASSES; pass++)
        {
          long word_idx;
          word_bb.clear();
          word_idx = snowblossomlib.PowUtil.getNextSnowFieldIndex(context, merkle_proof.getTotalWords(), md);
          merkle_proof.readWord(word_idx, word_bb);
          context = snowblossomlib.PowUtil.getNextContext(context, word_buff, md);
        }
      }


      byte[] found_hash = context;

      if (snowblossomlib.PowUtil.lessThanTarget(found_hash, b.getHeader().getTarget()))
      {
        String str = HashUtils.getHexString(found_hash);
        logger.info("Found passable solution: " + str);
        buildBlock(b, nonce, merkle_proof);

      }
      op_count.getAndIncrement();
    }

    private void buildBlock(Block b, byte[] nonce, SnowMerkleProof merkle_proof)
      throws Exception
    {
      Block.Builder bb = Block.newBuilder().mergeFrom(b);

      BlockHeader.Builder header = BlockHeader.newBuilder().mergeFrom( b.getHeader() );
      header.setNonce(ByteString.copyFrom(nonce));
      
      byte[] first_hash = snowblossomlib.PowUtil.hashHeaderBits(b.getHeader(), nonce);
      byte[] context = first_hash;

      for(int pass = 0; pass< snowblossomlib.Globals.POW_LOOK_PASSES; pass++)
      {
        word_bb.clear();

        long word_idx = snowblossomlib.PowUtil.getNextSnowFieldIndex(context, merkle_proof.getTotalWords());
        merkle_proof.readWord(word_idx, word_bb);
        SnowPowProof proof = merkle_proof.getProof(word_idx);
        header.addPowProof(proof);
        context = snowblossomlib.PowUtil.getNextContext(context, word_buff);
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
      while(!terminate)
      {
        boolean err=false;
        try(TimeRecordAuto tra = TimeRecord.openAuto("MinerThread.runPass"))
        {
          runPass();
        }
        catch(Throwable t)
        {
          err=true;
          logger.warning("Error: " + t);
        }

        if (err)
        {

          try(TimeRecordAuto tra = TimeRecord.openAuto("MinerThread.errorSleep"))
          {
            Thread.sleep(5000);
          }
          catch(Throwable t){}
        }

      }

    }
  
  }

  public class BlockTemplateEater implements StreamObserver<Block>
  {
    public void onCompleted(){}
    public void onError(Throwable t){}
    public void onNext(Block b)
    {
      logger.info("Got block template: height:" + b.getHeader().getBlockHeight() + " transactions:"  + b.getTransactionsCount() );


      int min_field = b.getHeader().getSnowField();

      
      logger.info("Required field: " + min_field + " - " + params.getSnowFieldInfo(min_field).getName() );
      
      int selected_field = -1;

      try
      {
        selected_field = field_scan.selectField(min_field);
        logger.info("Using field: " + selected_field + " - " + params.getSnowFieldInfo(selected_field).getName());

        try
        {
          field_scan.selectField(min_field+1);
        }
        catch(Throwable t)
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
      catch(Throwable t)
      {
        logger.info("Work block load error: " +t.toString());
        last_block_template = null;
      }
    }

  }


}
