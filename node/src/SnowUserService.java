package snowblossom.node;

import com.google.protobuf.ByteString;
import duckutil.LRUCache;
import duckutil.MetricLog;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import io.grpc.stub.StreamObserver;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.trie.proto.TrieNode;

public class SnowUserService extends UserServiceGrpc.UserServiceImplBase implements MemPoolTickleInterface
{
  private static final Logger logger = Logger.getLogger("snowblossom.userservice");

  private LinkedList<BlockSubscriberInfo> block_subscribers = new LinkedList<>();
  private HashMap<AddressSpecHash, LinkedList<StreamObserver<AddressUpdate> > > address_watchers = new HashMap<>();

  private SnowBlossomNode node;
  private Object tickle_trigger = new Object();
  private LRUCache<Integer, FeeEstimator> fee_est_map;

  public SnowUserService(SnowBlossomNode node)
  {
    super();

    this.node = node;

    fee_est_map = new LRUCache<>(2000);

  }

  public void start()
  {
    node.getMemPool().registerListener(this);
    new Tickler().start();
  }

  @Override
  public StreamObserver<SubscribeBlockTemplateRequest> subscribeBlockTemplateStream(StreamObserver<Block> responseObserver)
  {
    logger.log(Level.INFO, "Subscribe block template stream called");

    BlockSubscriberInfo info = new BlockSubscriberInfo(null, responseObserver, null);

    synchronized(block_subscribers)
    {
      block_subscribers.add(info);
    }

    return new TemplateUpdateObserver(info);

  }

  @Override
  public StreamObserver<SubscribeBlockTemplateRequest> subscribeBlockTemplateStreamExtended(StreamObserver<BlockTemplate> responseObserver)
  {
    logger.log(Level.INFO, "Subscribe block template stream called");

    BlockSubscriberInfo info = new BlockSubscriberInfo(null, null, responseObserver);

    synchronized(block_subscribers)
    {
      block_subscribers.add(info);
    }

    return new TemplateUpdateObserver(info);

  }


  @Override
  public void subscribeBlockTemplate(SubscribeBlockTemplateRequest req, StreamObserver<Block> responseObserver)
  {
    logger.log(Level.FINE, "Subscribe block template called");

    BlockSubscriberInfo info = new BlockSubscriberInfo(req, responseObserver, null);

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
      BlockTemplate bt = node.getBlockForge().getBlockTemplate(info.req);
      info.send(bt);
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

  @Override
  public void getPeerList(PeerListRequest req, StreamObserver<PeerList> ob)
  {
    ob.onNext(node.getPeerage().getPeerList(req));
  }

  @Override
  public void tickleMemPool(Transaction tx, Collection<AddressSpecHash> involved)
  {
    // TODO - shards
    ChainHash utxo_root = new ChainHash(node.getBlockIngestor(0).getHead().getHeader().getUtxoRootHash());
    sendAddressUpdates(involved, utxo_root);

  }

  @Override
  public void subscribeAddressUpdates(RequestAddress req, StreamObserver<AddressUpdate> ob)
  {
    AddressSpecHash hash = new AddressSpecHash( req.getAddressSpecHash() );

    synchronized(address_watchers)
    {
      if (!address_watchers.containsKey(hash))
      {
        address_watchers.put(hash, new LinkedList<StreamObserver<AddressUpdate>>());
      }
      address_watchers.get(hash).add(ob);
    }

    ChainHash utxo_root = new ChainHash(node.getBlockIngestor(0).getHead().getHeader().getUtxoRootHash());
    sendAddressUpdate(hash, utxo_root, ob);

  }

  private void sendAddressUpdates(Collection<AddressSpecHash> involved, ChainHash utxo_root)
  {
    synchronized(address_watchers)
    {
      for(AddressSpecHash hash : involved)
      {
        if (address_watchers.containsKey(hash))
        {
          LinkedList<StreamObserver<AddressUpdate> > ok_list = new LinkedList<>();
          for(StreamObserver<AddressUpdate> ob : address_watchers.get(hash))
          {
            try
            {
              sendAddressUpdate(hash, utxo_root, ob);
              ok_list.add(ob);
            }
            catch(Throwable t)
            {
              logger.info("Error: " + t);
            }
          }
           address_watchers.put(hash, ok_list);
        }
      }
    }

  }
  private void sendAddressUpdate(AddressSpecHash hash, ChainHash utxo_root, StreamObserver<AddressUpdate> ob)
  {
    AddressUpdate.Builder update = AddressUpdate.newBuilder();

    update.setAddress(hash.getBytes());
    update.setMempoolChanges( node.getMemPool().getTransactionsForAddress(hash).size() > 0);
    update.setCurrentUtxoRoot(utxo_root.getBytes());

    ob.onNext(update.build());

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
          // Could be null if stream hasn't started up yet
          if (info.req != null)
          {
            sendBlock(info);
          }
          continue_list.add(info);
        }
        catch(Throwable t)
        {
          logger.fine("Error: " + t);
        }
      }
      block_subscribers.clear();
      block_subscribers.addAll(continue_list);
    }
  }

  @Override
  public void submitBlock(Block block, StreamObserver<SubmitReply> responseObserver)
  {
    try(TimeRecordAuto tra = TimeRecord.openAuto("SnowUserService.submitBlock"))
    {
      try
      {
        logger.info("Submit block: " + node.getForgeInfo().getHeaderString(block.getHeader()));

        int shard_id = block.getHeader().getShardId();
        //TODO check to see if we are tracking that shard
        node.getBlockIngestor(shard_id).ingestBlock(block);
        logger.info("Accepted block: " + node.getForgeInfo().getBlockTextSummary(block.getHeader()));
      }
      catch(ValidationException e)
      {
        logger.info("Rejecting block: " + e);
        logger.info("Rejected block: " + node.getForgeInfo().getBlockTextSummary(block.getHeader()));

        responseObserver.onNext(SubmitReply.newBuilder()
            .setSuccess(false)
            .setErrorMessage("Rejecting block: " + e)
          .build());
        responseObserver.onCompleted();
        return;
      }

      responseObserver.onNext(SubmitReply.newBuilder().setSuccess(true).build());
      responseObserver.onCompleted();

    }
  }

  @Override
  public void submitTransaction(Transaction tx, StreamObserver<SubmitReply> responseObserver)
  {
    try(TimeRecordAuto tra = TimeRecord.openAuto("SnowUserService.submitTransaction"))
    {
      try
      {
        tx = Transaction.parseFrom(tx.toByteString());
        if (node.getMemPool().addTransaction(tx, false))
        {
          node.getTxBroadcaster().send(tx);
        }
        else
        {
          logger.fine("Rejecting transaction: no mempool accepted");

          responseObserver.onNext(SubmitReply.newBuilder()
            .setSuccess(false)
            .setErrorMessage("no mempool accepted")
            .build());
          responseObserver.onCompleted();
          return;

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

  }

  @Override
  public void getUTXONode(GetUTXONodeRequest request, StreamObserver<GetUTXONodeReply> responseObserver)
  {
    try(MetricLog mlog = new MetricLog())
    {
      mlog.setModule("SnowUserService");
      mlog.setOperation("GetUTXONode");
      ChainHash utxo_root = null;
      if (request.getUtxoTypeCase() == GetUTXONodeRequest.UtxoTypeCase.UTXO_ROOT_HASH)
      {
        utxo_root = new ChainHash(request.getUtxoRootHash());
      }
      else if (request.getUtxoTypeCase() == GetUTXONodeRequest.UtxoTypeCase.SHARD_ID)
      {
        int shard_id = request.getShardId();

        utxo_root = UtxoUpdateBuffer.EMPTY;
        BlockSummary summary = node.getBlockIngestor(shard_id).getHead();
        if (summary != null)
        {
          utxo_root = new ChainHash(summary.getHeader().getUtxoRootHash());
        }
      }
      else if (request.getUtxoTypeCase() == GetUTXONodeRequest.UtxoTypeCase.ALL_SHARDS)
      {
        responseObserver.onError(new Exception("Unsupported all_shards request - use other method"));
        return;

      }
      else
      {
        // Root of shard 0 case
        utxo_root = UtxoUpdateBuffer.EMPTY;
        BlockSummary summary = node.getBlockIngestor(0).getHead();
        if (summary != null)
        {
          utxo_root = new ChainHash(summary.getHeader().getUtxoRootHash());
        }

      }

      responseObserver.onNext(getUtxoNodeDetails(utxo_root, request));
      responseObserver.onCompleted();
    }
  }

  private GetUTXONodeReply getUtxoNodeDetails(ChainHash utxo_root, GetUTXONodeRequest request)
  {
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

    return reply.build();
  }

  @Override
  public void getUTXONodeMulti(GetUTXONodeRequest request, StreamObserver<GetUTXOReplyList> responseObserver)
  {
    try(MetricLog mlog = new MetricLog())
    {
      mlog.setModule("SnowUserService");
      mlog.setOperation("GetUTXONodeMulti");
 
      if (request.getUtxoTypeCase() != GetUTXONodeRequest.UtxoTypeCase.ALL_SHARDS)
      {
        responseObserver.onError(new Exception("unsupported type request - use other method"));
        return;
      }
      GetUTXOReplyList.Builder reply = GetUTXOReplyList.newBuilder();

      for(int s : node.getCurrentBuildingShards())
      {
        BlockSummary summary = node.getBlockIngestor(s).getHead();
        if (summary != null)
        {
          ChainHash utxo_root = new ChainHash(summary.getHeader().getUtxoRootHash());
          reply.putReplyMap(s, getUtxoNodeDetails( utxo_root, request ));
        }
      }

      responseObserver.onNext(reply.build());
      responseObserver.onCompleted();
    }
  }


  @Override
  public void getNodeStatus(NullRequest null_request, StreamObserver<NodeStatus> responseObserver)
  {
    try(MetricLog mlog = new MetricLog())
    {
      mlog.setModule("SnowUserService");
      mlog.setOperation("GetNodeStatus");

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

      for(Map.Entry<Integer, BlockHeader> me : node.getForgeInfo().getNetworkActiveShards().entrySet())
      {
        ns.putNetShardHeadMap(me.getKey(), me.getValue().getSnowHash());
      }

      for(int s : node.getCurrentBuildingShards())
      {
        ns.putShardHeadMap(s, node.getBlockIngestor(s).getHead().getHeader().getSnowHash());
      }
      ns.addAllNetworkActiveShards(node.getForgeInfo().getNetworkActiveShards().keySet());
      ns.addAllInterestShards(node.getInterestShards());

      responseObserver.onNext(ns.build());
      responseObserver.onCompleted();
    }
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
      int height = req.getBlockHeight();
      int shard = req.getShardId();
      ChainHash block_hash = node.getDB().getBlockHashAtHeight(shard, req.getBlockHeight());
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
  public void getBlockSummary(RequestBlockSummary req, StreamObserver<BlockSummary> responseObserver)
  {
    ChainHash block_hash = new ChainHash(req.getBlockHash());

    BlockSummary sum = node.getDB().getBlockSummaryMap().get(block_hash.getBytes());
    if (sum != null)
    {

      responseObserver.onNext(sum);
      responseObserver.onCompleted();
    }
    else
    {
      responseObserver.onError(new Exception("No such summary found"));
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
      block_hash = node.getDB().getBlockHashAtHeight(req.getShardId(), req.getBlockHeight());
    }

    BlockHeader answer = null;

    if (block_hash != null)
    {
      answer = node.getForgeInfo().getHeader(block_hash);
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
    status = TransactionMapUtil.getTxStatus(tx_id, node.getDB(), node.getBlockIngestor().getHead());

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
    try(MetricLog mlog = new MetricLog())
    {
      mlog.setModule("SnowUserService");
      mlog.setOperation("GetMempoolTransactionList");
      AddressSpecHash spec_hash = new AddressSpecHash(req.getAddressSpecHash());

      TransactionHashList.Builder list = TransactionHashList.newBuilder();
      for(ChainHash h : node.getMemPool().getTransactionsForAddress(spec_hash))
      {
        list.addTxHashes(h.getBytes());
      }

      observer.onNext( list.build());
      observer.onCompleted();
    }
  }

  @Override
  public void getMempoolTransactionMap(RequestAddress req, StreamObserver<TransactionShardMap> observer)
  {
    try(MetricLog mlog = new MetricLog())
    {
      mlog.setModule("SnowUserService");
      mlog.setOperation("GetMempoolTransactionMap");
      AddressSpecHash spec_hash = new AddressSpecHash(req.getAddressSpecHash());

      Map<Integer, Set<ChainHash> > m = node.getMemPool().getTransactionsForAddressByShard(spec_hash);

      TransactionShardMap.Builder shard_map = TransactionShardMap.newBuilder();

      for(Map.Entry<Integer, Set<ChainHash> > me : m.entrySet())
      {
        TransactionHashList.Builder lst = TransactionHashList.newBuilder();
        for(ChainHash h : me.getValue())
        {
          lst.addTxHashes(h.getBytes());
        }
        shard_map.putShardMap(me.getKey(), lst.build());
      }
      observer.onNext( shard_map.build() );
      observer.onCompleted();
    }
  }


  @Override
  public void getFeeEstimate(NullRequest null_request, StreamObserver<FeeEstimate> observer)
  {
    int shard_id = 0;
    FeeEstimate.Builder fe = FeeEstimate.newBuilder();
    fe.setFeePerByte( getFeeEstimator(shard_id).getFeeEstimate());

    for(int s : node.getCurrentBuildingShards())
    {
      fe.putShardMap(s, getFeeEstimator(s).getFeeEstimate());
    }

    observer.onNext( fe.build());
    observer.onCompleted();
  }

  public FeeEstimator getFeeEstimator(int shard_id)
  {
    synchronized(fee_est_map)
    {
      if (fee_est_map.containsKey(shard_id)) return fee_est_map.get(shard_id);

      FeeEstimator f = new FeeEstimator(node, shard_id);

      fee_est_map.put(shard_id, f);
      return f;
    }
  }

  @Override
  public void getAddressHistory(RequestAddress req, StreamObserver<HistoryList> observer)
  {
    try(MetricLog mlog = new MetricLog())
    {
      mlog.setModule("SnowUserService");
      mlog.setOperation("GetAddressHistory");
   
      AddressSpecHash spec_hash = new AddressSpecHash(req.getAddressSpecHash());
      try
      {
        HistoryList.Builder hl = HistoryList.newBuilder();
        for(int shard : node.getActiveShards())
        {
          hl.addAllEntries( 
            AddressHistoryUtil.getHistory(spec_hash, node.getDB(), node.getBlockIngestor(shard).getHead(), shard).getEntriesList() 
            );
        }
        observer.onNext( hl.build());
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
  }

  @Override
  public void getFBOList(RequestAddress req, StreamObserver<TxOutList> ob)
  {
    AddressSpecHash spec_hash = new AddressSpecHash(req.getAddressSpecHash());
    try
    {
      ob.onNext(ForBenefitOfUtil.getFBOList(spec_hash,
        node.getDB(),
        node.getBlockIngestor().getHead()));
      ob.onCompleted();
    }
    catch(Throwable e)
    {
      String addr = AddressUtil.getAddressString(node.getParams().getAddressPrefix(), spec_hash);
      logger.info("Exception "+addr+" " + e.toString());

      ob.onError(e);
      ob.onCompleted();
      return;
    }

  }

  @Override
  public void getIDList(RequestNameID req, StreamObserver<TxOutList> ob)
  {
    try
    {
      TxOutList lst = null;

      ByteString type = null;

      if (req.getNameType() == RequestNameID.IdType.USERNAME)
      {
        type = ForBenefitOfUtil.ID_MAP_USER;
      }
      if (req.getNameType() == RequestNameID.IdType.CHANNELNAME)
      {
        type = ForBenefitOfUtil.ID_MAP_CHAN;
      }
      lst = ForBenefitOfUtil.getIdList( type,
        req.getName(),
        node.getDB(),
        node.getBlockIngestor().getHead());
      //lst = ForBenefitOfUtil.filterByCurrent(lst);

      ob.onNext(lst);
      ob.onCompleted();
    }
    catch(Throwable e)
    {
      logger.info("Exception " + e.toString());

      ob.onError(e);
      ob.onCompleted();
      return;
    }

  }



  class BlockSubscriberInfo
  {
    volatile SubscribeBlockTemplateRequest req;
    private final StreamObserver<Block> sink;
    private final StreamObserver<BlockTemplate> template_sink;

    public BlockSubscriberInfo(SubscribeBlockTemplateRequest req,
      StreamObserver<Block> sink,
      StreamObserver<BlockTemplate> template_sink
      )
    {
      this.req = req;
      this.sink = sink;
      this.template_sink = template_sink;
    }

    public void updateTemplate(SubscribeBlockTemplateRequest req)
    {
      this.req = req;
    }

    public void send(BlockTemplate block_template)
    {
      if (sink != null)
      {
        if ((block_template == null) ||
           (block_template.getBlock().getHeader().getVersion() == 0))
        {
          sink.onNext(null);
        }
        else
        {
          sink.onNext(block_template.getBlock());
        }
      }
      if (template_sink != null)
      {
        template_sink.onNext(block_template);
      }

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

          //TODO - should maybe look at more than last block in case we missed a few
          if (node.getBlockIngestor().getHead() != null)
          {
            ChainHash head_block = new ChainHash(node.getBlockIngestor().getHead().getHeader().getSnowHash());

            Block b = node.getDB().getBlockMap().get(head_block.getBytes());
            ChainHash utxo_root_hash = new ChainHash(b.getHeader().getUtxoRootHash());

            HashSet<AddressSpecHash> involved = new HashSet<>();
            for(Transaction tx : b.getTransactionsList())
            {
              TransactionInner inner = TransactionUtil.getInner(tx);
              for (TransactionInput in : inner.getInputsList())
              {
                involved.add(new AddressSpecHash(in.getSpecHash()));
              }
              for (TransactionOutput out : inner.getOutputsList())
              {
                involved.add(new AddressSpecHash(out.getRecipientSpecHash()));
              }
            }

            sendAddressUpdates(involved, utxo_root_hash);
          }


        }
        catch(Throwable t)
        {
          logger.log(Level.INFO, "Tickle error: " + t);
        }
      }
    }

  }

  public class TemplateUpdateObserver implements StreamObserver<SubscribeBlockTemplateRequest>
  {
    public final BlockSubscriberInfo info;
    public TemplateUpdateObserver(BlockSubscriberInfo info)
    {
      this.info = info;

    }

    public void onNext(SubscribeBlockTemplateRequest req)
    {
      info.updateTemplate(req);

      // Send a new one immediately
      sendBlock(info);
    }

    public void onError(Throwable t)
    {
      logger.log(Level.INFO, "Error in TemplateUpdateObserver: " + t);
    }

    public void onCompleted()
    {

    }


  }
}
