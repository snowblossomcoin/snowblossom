package snowblossom.miner;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc;

import snowblossom.proto.SubscribeBlockTemplateRequest;
import snowblossom.proto.Block;
import snowblossom.proto.BlockHeader;
import snowblossom.proto.SnowPowProof;
import snowblossom.proto.SubmitReply;
import snowblossom.NetworkParams;
import snowblossom.NetworkParamsProd;
import snowblossom.NetworkParamsTestnet;


import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Random;
import snowblossom.PowUtil;
import snowblossom.Globals;
import snowblossom.SnowMerkleProof;
import snowblossom.trie.HashUtils;
import snowblossom.Config;
import snowblossom.ConfigFile;
import com.google.protobuf.ByteString;
import java.security.Security;
import java.io.File;

public class SnowBlossomMiner
{
  private static final Logger logger = Logger.getLogger("SnowBlossomMiner");

  public static void main(String args[]) throws Exception
  {
    Globals.addCryptoProvider();
    if (args.length != 1)
    {
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: SnowBlossomMiner <config_file>");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0]);


    new SnowBlossomMiner(config); 
  }

  private volatile Block last_block_template;

  private final UserServiceStub asyncStub;
  private final UserServiceBlockingStub blockingStub;

  private final FieldScan field_scan;
	private final NetworkParams params;

  public SnowBlossomMiner(Config config) throws Exception
  {
    config.require("snow_path");
    config.require("node_host");

    File path = new File(config.get("snow_path"));
    String host = config.get("node_host");
    int port = config.getIntWithDefault("node_port", 2338);
    
    params = NetworkParams.loadFromConfig(config);
		
    field_scan = new FieldScan(path, params);

    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();

    asyncStub = UserServiceGrpc.newStub(channel);
    blockingStub = UserServiceGrpc.newBlockingStub(channel);

    Random rnd = new Random();

    byte[] addr = new byte[Globals.ADDRESS_SPEC_HASH_LEN];
    rnd.nextBytes(addr);
    ByteString to_addr = ByteString.copyFrom(addr);


    asyncStub.subscribeBlockTemplate(SubscribeBlockTemplateRequest.newBuilder()
      .setPayRewardToSpecHash(to_addr).build(), new BlockTemplateEater());
    logger.info("Subscribed to blocks");  

    for(int i=0; i<4; i++)
    {
      new MinerThread().start();
    }

    while(true)
    {
      Thread.sleep(5000);
    }

  }

  public class MinerThread extends Thread
  {
    Random rnd;
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
        Thread.sleep(100);
        return;
      }
      byte[] nonce = new byte[Globals.NONCE_LEN];
      rnd.nextBytes(nonce);

      // TODO, modify headers to put snow field in
      byte[] first_hash = PowUtil.hashHeaderBits(b.getHeader(), nonce);

      SnowMerkleProof merkle_proof = field_scan.getFieldProof(b.getHeader().getSnowField());

      byte[] context = first_hash;
      for(int pass=0; pass<Globals.POW_LOOK_PASSES; pass++)
      {
        long word_idx = PowUtil.getNextSnowFieldIndex(context, merkle_proof.getTotalWords());
        byte[] word = merkle_proof.readWord(word_idx);
        context = PowUtil.getNextContext(context, word);
      }


      byte[] found_hash = context;

      if (PowUtil.lessThanTarget(found_hash, b.getHeader().getTarget()))
      {
        String str = HashUtils.getHexString(found_hash);
        logger.info("Found passable solution: " + str);
        buildBlock(b, nonce, merkle_proof);

      }
    }

    private void buildBlock(Block b, byte[] nonce, SnowMerkleProof merkle_proof)
      throws Exception
    {
      Block.Builder bb = Block.newBuilder().mergeFrom(b);

      BlockHeader.Builder header = BlockHeader.newBuilder().mergeFrom( b.getHeader() );
      header.setNonce(ByteString.copyFrom(nonce));
      
      byte[] first_hash = PowUtil.hashHeaderBits(b.getHeader(), nonce);
      byte[] context = first_hash;
      for(int pass=0; pass<Globals.POW_LOOK_PASSES; pass++)
      {
        long word_idx = PowUtil.getNextSnowFieldIndex(context, merkle_proof.getTotalWords());
        byte[] word = merkle_proof.readWord(word_idx);
        SnowPowProof proof = merkle_proof.getProof(word_idx);
        header.addPowProof(proof);
        context = PowUtil.getNextContext(context, word);
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
      while(true)
      {
        boolean err=false;
        try
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

          try
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
      logger.info("Got block template: " + b);

      int min_field = b.getHeader().getSnowField();
      
      int selected_field = -1;

      try
      {
        selected_field = field_scan.selectField(min_field);

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
