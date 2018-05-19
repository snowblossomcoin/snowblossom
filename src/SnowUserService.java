package snowblossom;

import snowblossom.proto.UserServiceGrpc;
import snowblossom.proto.SubscribeBlockTemplateRequest;
import snowblossom.proto.Block;
import snowblossom.proto.Transaction;
import snowblossom.proto.BlockHeader;
import snowblossom.proto.BlockSummary;
import snowblossom.proto.SubmitReply;
import snowblossom.proto.GetUTXONodeRequest;
import snowblossom.proto.GetUTXONodeReply;
import snowblossom.trie.proto.TrieNode;
import io.grpc.stub.StreamObserver;

import java.util.Random;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.google.protobuf.ByteString;
import java.nio.ByteBuffer;

import java.util.LinkedList;
import java.util.TreeMap;
import java.util.List;


public class SnowUserService extends UserServiceGrpc.UserServiceImplBase
{
  private static final Logger logger = Logger.getLogger("snowblossom.userservice");

  private LinkedList<BlockSubscriberInfo> block_subscribers = new LinkedList<>();

  private SnowBlossomNode node;
  private Object tickle_trigger = new Object();

  public SnowUserService(SnowBlossomNode node)
  {
    super();

    this.node = node;


  }

  public void start()
  {
    new Tickler().start();
  }


  @Override
  public void subscribeBlockTemplate(SubscribeBlockTemplateRequest req, StreamObserver<Block> responseObserver)
  {
    logger.info("Subscribe block template called");
    AddressSpecHash pay_to = new AddressSpecHash(req.getPayRewardToSpecHash());


    BlockSubscriberInfo info = new BlockSubscriberInfo(pay_to, responseObserver);

    synchronized(block_subscribers)
    {
      block_subscribers.add(info);
    }

    sendBlock(info);
  }

  protected void sendBlock(BlockSubscriberInfo info)
  {
    Block block = node.getBlockForge().getBlockTemplate(info.mine_to);
    info.sink.onNext(block);

  }

  /**
   * Trigger new blocks being send to block subscribers
   */
  public void tickleBlocks()
  {
    synchronized(tickle_trigger)
    {
      tickle_trigger.notifyAll(); 
    }
  }

  private void sendNewBlocks()
  {
    synchronized(block_subscribers)
    {
      LinkedList<BlockSubscriberInfo> continue_list = new LinkedList<>();

      for(BlockSubscriberInfo info : block_subscribers)
      {
        try
        {
          sendBlock(info);
          continue_list.add(info);
        }
        catch(Throwable t)
        {
          logger.info("Error: " + t);
        }
      }
      block_subscribers.clear();
      block_subscribers.addAll(continue_list);
    }
  }

  @Override
  public void submitBlock(Block block, StreamObserver<SubmitReply> responseObserver)
  {
    try
    {
      node.getBlockIngestor().ingestBlock(block);
    }
    catch(ValidationException e)
    {
      logger.info("Rejecting block: " + e);

      responseObserver.onNext(SubmitReply.newBuilder()
          .setSuccess(false)
          .setErrorMessage(e.toString())
        .build());
      responseObserver.onCompleted();
      return;
    }

    responseObserver.onNext(SubmitReply.newBuilder().setSuccess(true).build());
    responseObserver.onCompleted();
  
  }

  @Override
  public void submitTransaction(Transaction tx, StreamObserver<SubmitReply> responseObserver)
  {
    try
    {
      node.getMemPool().addTransaction(tx);
      node.getPeerage().broadcastTransaction(tx);
    }
    catch(ValidationException e)
    {
      logger.info("Rejecting transaction: " + e);

      responseObserver.onNext(SubmitReply.newBuilder()
          .setSuccess(false)
          .setErrorMessage(e.toString())
        .build());
      responseObserver.onCompleted();
      return;
    }

    responseObserver.onNext(SubmitReply.newBuilder().setSuccess(true).build());
    responseObserver.onCompleted();
  
  }

  @Override
  public void getUTXONode(GetUTXONodeRequest request, StreamObserver<GetUTXONodeReply> responseObserver)
  {
    ChainHash utxo_root = null;
    if (request.getUtxoRootHash().size() > 0)
    {
      utxo_root = new ChainHash(request.getUtxoRootHash());
    }
    else
    {
      utxo_root = UtxoUpdateBuffer.EMPTY;
      BlockSummary summary = node.getBlockIngestor().getHead();
      if (summary != null)
      {
      utxo_root = new ChainHash(summary.getHeader().getUtxoRootHash());
      }
    }

    ByteString target=request.getPrefix();

    LinkedList<TrieNode> proof = new LinkedList<>();
    LinkedList<TrieNode> results = new LinkedList<>();
    int max_results = 10000;
    if (request.getMaxResults() > 0) max_results = request.getMaxResults();


    node.getUtxoHashedTrie().getNodeDetails(utxo_root.getBytes(), target, proof, results, max_results);

    GetUTXONodeReply.Builder reply = GetUTXONodeReply.newBuilder();

    reply.setUtxoRootHash(utxo_root.getBytes());
    reply.addAllAnswer(results);
    if (request.getIncludeProof())
    {
      reply.addAllProof(proof);
    }

    responseObserver.onNext(reply.build());
    responseObserver.onCompleted();
  }

  class BlockSubscriberInfo
  {
    final AddressSpecHash mine_to;
    final StreamObserver<Block> sink;

    public BlockSubscriberInfo(AddressSpecHash mine_to, StreamObserver<Block> sink)
    {
      this.mine_to = mine_to;
      this.sink = sink;
    }
  }

  public class Tickler extends Thread
  {
    public Tickler()
    {
      setName("SnowUserService/Tickler");
      setDaemon(true);
    }

    public void run()
    {
      while(true)
      {
        try
        {
          synchronized(tickle_trigger)
          {
            tickle_trigger.wait(30000);
          }
          sendNewBlocks();
        }
        catch(Throwable t)
        {
          logger.log(Level.INFO, "Tickle error: " + t);
        }
      }
    }

  }
}

