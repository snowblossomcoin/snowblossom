package snowblossom.node;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import snowblossom.proto.*;
import snowblossom.lib.*;
import snowblossom.trie.proto.TrieNode;

import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;


public class SnowUserService extends UserServiceGrpc.UserServiceImplBase
{
  private static final Logger logger = Logger.getLogger("snowblossom.userservice");

  private LinkedList<BlockSubscriberInfo> block_subscribers = new LinkedList<>();

  private SnowBlossomNode node;
  private Object tickle_trigger = new Object();
  private FeeEstimator fee_est;

  public SnowUserService(SnowBlossomNode node)
  {
    super();

    this.node = node;

    fee_est = new FeeEstimator(node);


  }

  public void start()
  {
    new Tickler().start();
  }


  @Override
  public void subscribeBlockTemplate(SubscribeBlockTemplateRequest req, StreamObserver<Block> responseObserver)
  {
    logger.log(Level.FINE, "Subscribe block template called");

    BlockSubscriberInfo info = new BlockSubscriberInfo(req, responseObserver);

    synchronized(block_subscribers)
    {
      block_subscribers.add(info);
    }

    sendBlock(info);
  }

  protected void sendBlock(BlockSubscriberInfo info)
  {
    if (node.areWeSynced())
    {
      Block block = node.getBlockForge().getBlockTemplate(info.req);
      info.sink.onNext(block);
    }
    else
    {
      logger.log(Level.WARNING, "We are not yet synced, refusing to send block template to miner");
    }
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
      tx = Transaction.parseFrom(tx.toByteString());
      if (node.getMemPool().addTransaction(tx))
      {
        node.getPeerage().broadcastTransaction(tx);
      }
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
    catch(com.google.protobuf.InvalidProtocolBufferException e)
    {
      logger.info("Rejecting transaction, strange error: " + e);
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
    if (request.getMaxResults() > 0) max_results = Math.min(max_results, request.getMaxResults());


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

  @Override
  public void getNodeStatus(NullRequest null_request, StreamObserver<NodeStatus> responseObserver)
  {
    NodeStatus.Builder ns = NodeStatus.newBuilder();

    ns
      .setMemPoolSize(node.getMemPool().getMemPoolSize())
      .setConnectedPeers(node.getPeerage().getConnectedPeerCount())
      .setEstimatedNodes(node.getPeerage().getEstimateUniqueNodes())
      .setNodeVersion(Globals.VERSION)
      .putAllVersionMap(node.getPeerage().getVersionMap());

    ns.setNetwork( node.getParams().getNetworkName() );

    if (node.getBlockIngestor().getHead() != null)
    {
      ns.setHeadSummary(node.getBlockIngestor().getHead());
    }

    responseObserver.onNext(ns.build());
    responseObserver.onCompleted();
  }

  @Override
  public void getBlock(RequestBlock req, StreamObserver<Block> responseObserver)
  {
    if (req.getBlockHash().size() > 0)
    {
      Block blk = node.getDB().getBlockMap().get(req.getBlockHash());
      responseObserver.onNext(blk);
      responseObserver.onCompleted();
    }
    else
    {
      ChainHash block_hash = node.getDB().getBlockHashAtHeight(req.getBlockHeight());
      if (block_hash == null)
      {
        responseObserver.onNext(null);
        responseObserver.onCompleted();
        return;
      }
      Block blk = node.getDB().getBlockMap().get(block_hash.getBytes());
      responseObserver.onNext(blk);
      responseObserver.onCompleted();
    }
  }

  @Override
  public void getBlockHeader(RequestBlockHeader req, StreamObserver<BlockHeader> responseObserver)
  {
    ChainHash block_hash = null;
    if (req.getBlockHash().size() > 0)
    {
      block_hash = new ChainHash(req.getBlockHash());
    }
    else
    {
      block_hash = node.getDB().getBlockHashAtHeight(req.getBlockHeight());
    }

    BlockHeader answer = null;

    if (block_hash != null)
    {
      BlockSummary sum = node.getDB().getBlockSummaryMap().get(block_hash.getBytes());
      answer = sum.getHeader();
    }
    responseObserver.onNext(answer);
    responseObserver.onCompleted();
  }

  @Override
  public void getTransaction(RequestTransaction req, StreamObserver<Transaction> observer)
  {
    Transaction tx = node.getDB().getTransactionMap().get(req.getTxHash());

    if (tx == null)
    {
      tx = node.getMemPool().getTransaction(new ChainHash(req.getTxHash()));
    }

    observer.onNext(tx);
    observer.onCompleted();
  }

  @Override
  public void getTransactionStatus(RequestTransaction req, StreamObserver<TransactionStatus> observer)
  {
    ChainHash tx_id = new ChainHash(req.getTxHash());

    TransactionStatus status = null;
    status = TransactionMapUtil.getTxStatus(tx_id, node.getDB(), node.getBlockHeightCache(), node.getBlockIngestor().getHead());

    if (status.getUnknown())
    {
      if (node.getMemPool().getTransaction(tx_id) != null)
      {
        status = TransactionStatus.newBuilder().setMempool(true).build();
      }
    }

    observer.onNext(status);
    observer.onCompleted();

  }

  @Override
  public void getMempoolTransactionList(RequestAddress req, StreamObserver<TransactionHashList> observer)
  {
    AddressSpecHash spec_hash = new AddressSpecHash(req.getAddressSpecHash());

    TransactionHashList.Builder list = TransactionHashList.newBuilder();
    for(ChainHash h : node.getMemPool().getTransactionsForAddress(spec_hash))
    {
      list.addTxHashes(h.getBytes());
    }

    observer.onNext( list.build());
    observer.onCompleted();
  }

  @Override
  public void getFeeEstimate(NullRequest null_request, StreamObserver<FeeEstimate> observer)
  {
    //observer.onNext( FeeEstimate.newBuilder().setFeePerByte( Globals.BASIC_FEE ).build() );
    observer.onNext( FeeEstimate.newBuilder().setFeePerByte( fee_est.getFeeEstimate()).build());
    observer.onCompleted();
  }

  @Override
  public void getAddressHistory(RequestAddress req, StreamObserver<HistoryList> observer)
  {
    AddressSpecHash spec_hash = new AddressSpecHash(req.getAddressSpecHash());
    try
    {

      observer.onNext( AddressHistoryUtil.getHistory(spec_hash, node.getDB(), node.getBlockHeightCache()) );
      observer.onCompleted();


    }
    catch(ValidationException e)
    {
      observer.onNext(HistoryList.newBuilder().setNotEnabled(true).build());
      observer.onCompleted();

    }
    catch(Throwable e)
    {
      String addr = AddressUtil.getAddressString(node.getParams().getAddressPrefix(), spec_hash);
      logger.info("Exception "+addr+" " + e.toString());

      observer.onNext(HistoryList.newBuilder().build());
      observer.onError(e);
      observer.onCompleted();
      return;
    }
 
  }


  class BlockSubscriberInfo
  {
    final SubscribeBlockTemplateRequest req;
    final StreamObserver<Block> sink;

    public BlockSubscriberInfo(SubscribeBlockTemplateRequest req, StreamObserver<Block> sink)
    {
      this.req = req;
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

