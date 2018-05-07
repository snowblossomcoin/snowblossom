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
import com.google.protobuf.ByteString;
import java.nio.ByteBuffer;

import java.util.LinkedList;
import java.util.TreeMap;


/**
 * Everything in this class is a disaster.  The syncornization is all wrong.
 * We have no rational control of anything.  A bunch of this block logic needs to be moved
 * out and this should share a block subscriber list with the module doing the p2p networking.
 *
 * basically, kill it with fire
 */
public class SnowUserService extends UserServiceGrpc.UserServiceImplBase
{
  private static final Logger logger = Logger.getLogger("SnowUserService");

  private volatile Block last_sent_block;
  private AddressSpecHash pay_to;
  private LinkedList<StreamObserver<Block> > block_subscribers = new LinkedList<>();

  private SnowBlossomNode node;

  public SnowUserService(SnowBlossomNode node)
  {
    super();

    this.node = node;


  }


  @Override
  public synchronized void subscribeBlockTemplate(SubscribeBlockTemplateRequest req, StreamObserver<Block> responseObserver)
  {
    logger.info("Subscribe block template called");
    pay_to = new AddressSpecHash(req.getPayRewardToSpecHash());

    synchronized(block_subscribers)
    {
      block_subscribers.add(responseObserver);
    }
    if (last_sent_block == null) tickleBlocks();
    else
    {
      responseObserver.onNext(last_sent_block);
    }

  }

  protected synchronized void tickleBlocks()
  {

    if (pay_to == null) return;
    last_sent_block = node.getBlockForge().getBlockTemplate(pay_to);

    synchronized(block_subscribers)
    {
      LinkedList<StreamObserver<Block>> continue_list = new LinkedList<>();

      for(StreamObserver<Block> responseObserver : block_subscribers)
      {
        try
        {
          responseObserver.onNext(last_sent_block);
          continue_list.add(responseObserver);
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
  public synchronized void submitBlock(Block block, StreamObserver<SubmitReply> responseObserver)
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
  
    tickleBlocks();
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
    int max_results = 1000;
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

}

