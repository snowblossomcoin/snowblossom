package snowblossom.node;

import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.ListMultimap;
import duckutil.MetricLog;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.HashSet;
import java.util.Collection;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.lib.tls.MsgSigUtil;
import snowblossom.proto.*;

/**
 * This class exists to present a single view of a peer regardless
 * of if we are the client or server.  We don't really care.
 * Messages to the other side go out on the 'sink'.
 * Messages come in on the onNext() method.
 */
public class PeerLink implements StreamObserver<PeerMessage>
{
  private static final Logger logger = Logger.getLogger("snowblossom.peering");

  private SnowBlossomNode node;
  private StreamObserver<PeerMessage> sink;
  private ManagedChannel channel;
  private volatile boolean closed;

  private boolean server_side;
  private String link_id;
  private long last_received_message_time;
  private boolean got_first_tip = false;
  private PeerInfo peer_info; //set immediately when we are client, set eventually otherwise

  private Object plan_lock = new Object();

  // Set of hashes that we have some sort of a plan to get already
  private HashSet<ChainHash> plan_block_set = new HashSet<>();

  // Maps hash of a block to the set of blocks we want to download
  // once we have it
  private SetMultimap<ChainHash, ChainHash> desire_block_map =
                     MultimapBuilder.hashKeys().hashSetValues().build();

  public PeerLink(SnowBlossomNode node, StreamObserver<PeerMessage> sink)
  {
    this.node = node;
    this.sink = sink;
    server_side=true;
    setLinkId();
    last_received_message_time = System.currentTimeMillis();

  }

  public PeerLink(SnowBlossomNode node, String link_id, PeerInfo info)
  {
    this.node = node;
    server_side=false;

    this.link_id = link_id;
    this.peer_info = info;
    last_received_message_time = System.currentTimeMillis();
  }

  private void setLinkId()
  {
    Random rnd = new Random();
    byte[] b=new byte[6];
    rnd.nextBytes(b);

    link_id = HexUtil.getHexString(b);

  }
  public void setSink(StreamObserver<PeerMessage> sink)
  {
    this.sink=sink;
  }
  public void setChannel(ManagedChannel channel)
  {
    this.channel = channel;
  }

  @Override
  public void onCompleted()
  {
    close();
  }

  @Override
  public void onError(Throwable t)
  {
    logger.log(Level.FINEST,"link error: " + t);
    close();
  }


  /**
   * This peer syncing is ever so much fun.  The basic contract is that each side sends its
   * PeerChainTip on connect, on each new block, and every 10 seconds.  Then the other side has to
   * ask if it is interested in anything.
   *
   * So when a side receives a tip, it decides if it wants what the peer is selling.
   */
  @Override
  public void onNext(PeerMessage msg)
  {

    last_received_message_time = System.currentTimeMillis();
    MetricLog mlog = new MetricLog();
    try
    {
      mlog.setOperation("peer_message");
      mlog.setModule("peer_link");
      mlog.set("peer", getLinkId());
      mlog.set("size", msg.toByteString().size());

      if (msg.hasTx())
      {
        Transaction tx = msg.getTx();
        mlog.set("type","tx");
        //logger.info("TX: " + new ChainHash(tx.getTxHash()));
        try
        {
          if (node.getMemPool().addTransaction(tx, true))
          {
            node.getTxBroadcaster().send(tx);
          }
          else
          {
            //logger.info("Chill false");
          }
        }
        catch(ValidationException e)
        {
          if (e.toString().contains("Unable to find source tx"))
          {
            if (node.areWeSynced())
            {
              ChainHash tx_id =  new ChainHash(tx.getTxHash());

              if (node.getBlockIngestor().reserveTxCluster(tx_id))
              {
                logger.fine("Requesting cluster for tx: " +  tx_id);
                writeMessage( PeerMessage.newBuilder()
                  .setReqCluster(
                    RequestTransaction.newBuilder().setTxHash(tx.getTxHash()).build())
                .build());
              }
            }
          }
        }
        // do not care about tx validation errors from peers
      }
      else if (msg.hasTip())
      {
        mlog.set("type","tip");
        PeerChainTip tip = msg.getTip();
        if (!node.getParams().getNetworkName().equals(tip.getNetworkName()))
        {
          logger.log(Level.FINE, String.format("Peer has wrong name: %s", tip.getNetworkName()));
          close();
          return;
        }
        node.getPeerage().reportTip();

        try(MetricLog mlog_sub = new MetricLog(mlog, "tip_trust"))
        {
          List<ChainHash> req_import_blocks = node.getShardUtxoImport().checkTipTrust(mlog_sub, msg.getTip());
          if (req_import_blocks != null)
          {
            for(ChainHash h : req_import_blocks)
            {
              logger.log(Level.FINE, "Requesting Import Block: " + h);
              writeMessage( PeerMessage.newBuilder().setReqImportBlock(
                RequestImportedBlock.newBuilder().setBlockHash(h.getBytes()).build())
                .build());
            }
            mlog.set("req_imp_block_count", req_import_blocks.size());
          }
        }
        checkTipForInterestThing(msg.getTip());


        // When we first get a tip from a node we connected to
        // update the peer info showing the success in getting a tip
        if ((!got_first_tip) && (peer_info != null))
        {
          logger.log(Level.FINE, "Saving last passed");
          got_first_tip=true;
          PeerInfo pi = PeerInfo.newBuilder().mergeFrom(peer_info).setLastPassed(System.currentTimeMillis()).build();
          node.getPeerage().learnPeer(pi);
        }


        BlockHeader header = tip.getHeader();
        if (header.getSnowHash().size() > 0)
        {
          Validation.checkBlockHeaderBasics(node.getParams(), header, false);
          mlog.set("head_hash", new ChainHash(header.getSnowHash()).toString());
          mlog.set("head_shard", header.getShardId());
          mlog.set("head_height", header.getBlockHeight());
          considerBlockHeader(header, header.getShardId());
          node.getPeerage().setHighestHeader(header);
        }

        // save first peer info as opposite side
        if (tip.getPeersCount() > 0)
        {
          peer_info = tip.getPeers(0); // first entry is host we are talking to

        }
        for(PeerInfo pi : tip.getPeersList())
        {
          if (PeerUtil.isSane(pi))
          {
            node.getPeerage().learnPeer(pi);
          }
        }
      }
      else if (msg.hasReqBlock())
      {
        mlog.set("type","req_block");
        // Other side is asking for a block
        ChainHash hash = new ChainHash(msg.getReqBlock().getBlockHash());
        mlog.set("hash", hash.toString());
        logger.fine("Got block request: " + hash);
        Block blk = node.getDB().getBlockMap().get(hash.getBytes());
        if (blk != null)
        {
          writeMessage( PeerMessage.newBuilder().setBlock(blk).build() );
        }
      }
      else if (msg.hasBlock())
      {
        mlog.set("type","block");
        // Getting a block, we probably asked for it.  See if we can eat it.
        Block blk = msg.getBlock();
        synchronized(desire_block_map)
        {
          desire_block_map.remove(
            new ChainHash(blk.getHeader().getPrevBlockHash()),
            new ChainHash(blk.getHeader().getSnowHash()));
        }

        mlog.set("hash", new ChainHash(blk.getHeader().getSnowHash()).toString());
        try
        {
          logger.fine(String.format("Got block shard:%d height:%d %s ",
            blk.getHeader().getShardId(),
            blk.getHeader().getBlockHeight(),
            new ChainHash(blk.getHeader().getSnowHash()).toString() ));
          // will only open if we are actually interested in this shard
          node.openShard(blk.getHeader().getShardId());
          if (node.getBlockIngestor(blk.getHeader().getShardId()).ingestBlock(blk))
          { // we could eat it, think about getting more blocks
            scanForBlocksToRequest();

            // Think about getting more blocks from desire map
          }
        }
        catch(ValidationException ve)
        {
          logger.info("Got a block %s that didn't validate - closing link");
          close();
          throw(ve);
        }
      }
      else if (msg.hasReqHeader())
      {
        mlog.set("type","req_header");
        ChainHash hash;
        int shard = msg.getReqHeader().getShardId();
        mlog.set("shard", shard);

        if (msg.getReqHeader().getBlockHash().size() > 0)
        {
          hash = new ChainHash(msg.getReqHeader().getBlockHash());
        }
        else
        {
          // Peer is asking for a block header
          int height = msg.getReqHeader().getBlockHeight();
          mlog.set("height", height);
          hash = node.getDB().getBlockHashAtHeight(shard, height);
        }

        // Note: this could be returning headers in parent shards
        // since we are recording them all in the height map
        if (hash != null)
        {
          mlog.set("hash", hash.toString());
          BlockSummary summary = node.getDB().getBlockSummaryMap().get(hash.getBytes());
          if (summary == null)
          {
            mlog.set("missing_summary", 1);
          }
          else
          {
            writeMessage( PeerMessage.newBuilder().setHeader(summary.getHeader()).setReqHeaderShardId(shard).build() );
          }
        }
      }
      else if (msg.hasHeader())
      {
        mlog.set("type","header");
        // We got a header, probably one we asked for
        BlockHeader header = msg.getHeader();
        mlog.set("hash", new ChainHash(header.getSnowHash()).toString());
        Validation.checkBlockHeaderBasics(node.getParams(), header, false);
        mlog.set("head_hash", new ChainHash(header.getSnowHash()).toString());
        mlog.set("head_shard", header.getShardId());
        mlog.set("head_height", header.getBlockHeight());
        considerBlockHeader(header, msg.getReqHeaderShardId());
      }
      else if (msg.hasReqCluster())
      {
        mlog.set("type","req_cluster");

        ChainHash tx_id = new ChainHash(msg.getReqCluster().getTxHash());
        mlog.set("hash", tx_id.toString());
        sendCluster(tx_id);
      }
      else if (msg.hasReqImportBlock())
      {
        mlog.set("type", "req_import_block");
        ChainHash hash = new ChainHash(msg.getReqImportBlock().getBlockHash());
        mlog.set("hash", hash.toString());

        ImportedBlock b = node.getShardUtxoImport().getImportBlock(hash);
        if (b != null)
        {
          writeMessage( PeerMessage.newBuilder().setImportBlock(b).build() );
        }
      }
      else if (msg.hasImportBlock())
      {
        mlog.set("type", "import_block");

        BlockHeader header = msg.getImportBlock().getHeader();
        mlog.set("hash", new ChainHash(header.getSnowHash()).toString());
        mlog.set("shard_id", header.getShardId());
        mlog.set("height", header.getBlockHeight());

        node.getShardUtxoImport().addImportedBlock(msg.getImportBlock());
      }
      else if (msg.hasReqPreviewChain())
      {
        RequestPreviewChain req = msg.getReqPreviewChain();

        PreviewChain chain = node.getForgeInfo().getPreviewChain(
          new ChainHash(req.getStartBlockHash()), 
          req.getRequestedBlocksBack());
        writeMessage( PeerMessage.newBuilder().setPreviewChain(chain).build() );
      }
      else if (msg.hasPreviewChain())
      {
        PreviewChain pre_chain = msg.getPreviewChain();

        investigatePreviews( pre_chain.getPreviewsList() ); 
      }
    }
    catch(ValidationException e)
    {
      mlog.set("exception", e.toString());
      logger.log(Level.INFO, "Some validation error from " + getLinkId(), e);
    }
    catch(Throwable e)
    {
      mlog.set("exception", e.toString());
      logger.log(Level.INFO, "Some bs from " + getLinkId(), e);
      close();
    }
    finally
    {
      mlog.close();
    }
  }

  private void sendCluster(ChainHash tx_id)
  {
    List<Transaction> tx_list = node.getMemPool().getTxClusterForTransaction(tx_id);
    if (tx_list == null) return;

    logger.fine("Sending cluster for " + tx_id + " - " + tx_list.size() + " transactions");

    for(Transaction tx : tx_list)
    {
      writeMessage( PeerMessage.newBuilder()
        .setTx(tx).build());
    }
  }
  
  private void investigatePreviews(Collection<BlockPreview> list)
  {
    // Investigate them in ascending block height order

    ListMultimap<Integer, BlockPreview> preview_map =
      MultimapBuilder.treeKeys(). linkedListValues().build();
    
    for(BlockPreview bp : list)
    {
      preview_map.put( bp.getBlockHeight(), bp);
    }
    for(BlockPreview bp : preview_map.values())
    {
      investigatePreview(bp);
    }
  }

  /** See if we want to do anything with this preview */
  private void investigatePreview(BlockPreview bp)
  {
    int shard_id = bp.getShardId();
    if (!node.getInterestShards().contains(bp.getShardId())) return;
    ChainHash hash = new ChainHash(bp.getSnowHash());
    ChainHash prev = new ChainHash(bp.getPrevBlockHash());

    boolean prev_plan;
    synchronized(plan_lock)
    {
      if (plan_block_set.contains(hash)) return; //already have a plan
      prev_plan = plan_block_set.contains(prev);
    }

    if (node.getForgeInfo().getSummary(hash) != null) return; //already have it

    if (bp.getBlockHeight() == 0)
    {
      logger.info("Requesting block: " + hash);
        writeMessage( PeerMessage.newBuilder()
          .setReqBlock(
            RequestBlock.newBuilder().setBlockHash(hash.getBytes()).build())
          .build());
      return;
    }

    boolean prev_db = (node.getForgeInfo().getSummary(prev) != null);
    if (prev_db)
    {
      // Request it now
      if (node.getBlockIngestor(0).reserveBlock(hash))
      {
        logger.info("Requesting block: " + hash);
        writeMessage( PeerMessage.newBuilder()
          .setReqBlock(
            RequestBlock.newBuilder().setBlockHash(hash.getBytes()).build())
          .build());
      }
      return;
    }
    else
    {

      if (!prev_plan)
      {
        // If we don't have a plan to get the previous block
        // and we don't have the previous block
        // then request more previews

        logger.info(String.format("Requesting preview at %d - %s", bp.getBlockHeight()-1, prev));
        writeMessage( PeerMessage.newBuilder()
          .setReqPreviewChain(
            RequestPreviewChain.newBuilder()
              .setStartBlockHash( prev.getBytes() )
              .setRequestedBlocksBack( Globals.MAX_PREVIEW_CHAIN_LENGTH )
              .build()
            )
           .build());
      }

    }

    synchronized(plan_lock)
    {
      plan_block_set.add(hash);
      desire_block_map.put(prev, hash);
    }

  }

  private void checkTipForInterestThing(PeerChainTip tip)
    throws ValidationException
  {
    if (tip.getHeader().getSnowHash().size() == 0) return;
    if (tip.getSignedHead() == null) return;
    if (tip.getSignedHead().getPayload().size() == 0) return;

    // We are not checking signature is by someone we care about
    // So take anything here as a pack of usual p2p lies
    SignedMessagePayload payload = MsgSigUtil.validateSignedMessage(tip.getSignedHead(), node.getParams());

    PeerTipInfo tip_info = payload.getPeerTipInfo();

    investigatePreviews(tip_info.getPreviewsList());
  }


  /**
   * This might be pretty slow - maybe not use this so much
   */
  private void scanForBlocksToRequest()
  {
    synchronized(desire_block_map)
    {
      for(ChainHash parent : desire_block_map.keySet())
      {
        if (node.getDB().getBlockSummaryMap().get(parent.getBytes())!=null)
        {
          for(ChainHash child : desire_block_map.get(parent))
          {
            if (node.getDB().getBlockSummaryMap().get(child.getBytes())==null)
            {
              if (node.getBlockIngestor(0).reserveBlock(child))
              {
                logger.info("Requesting block from desire map: " + child);
                writeMessage( PeerMessage.newBuilder()
                  .setReqBlock(
                    RequestBlock.newBuilder().setBlockHash(child.getBytes()).build())
                  .build());
              }
            }

          }

        }
      }

    }

  }

  /**
   * The basic plan is, keep asking about previous blocks
   * until we get to one we have heard of.  Then we start requesting the blocks.
   */
  private void considerBlockHeader(BlockHeader header, int context_shard_id)
  {
    investigatePreview( BlockchainUtil.getPreview(header) );
    int shard_id = header.getShardId();
    node.tryOpenShard(shard_id);

    if (!node.getActiveShards().contains(shard_id)) return;

    node.getDB().getBlockHeaderMap().put( header.getSnowHash(), header);
    node.getDB().getChildBlockMapSet().add(header.getPrevBlockHash(), header.getSnowHash());

  }

  public void close()
  {
    if (closed) return;
    closed=true;
    try
    {

      if (sink != null)
      {
        sink.onCompleted();
      }

      if (channel != null)
      {
        channel.shutdownNow();
        if (!channel.awaitTermination(5, TimeUnit.SECONDS))
        {
          logger.info(getLinkId() + " awaitTermination returned false.");
        }
      }
    }
    catch(Throwable e){}
  }

  public boolean isOpen()
  {
    if (last_received_message_time + 300000L < System.currentTimeMillis())
    {
      logger.info(getLinkId() + " - No message in a long time, assuming dead link");
      close();
    }
    return !closed;
  }

  public void writeMessage(PeerMessage msg)
  {
    if (!closed)
    {
      synchronized(sink)
      {
        sink.onNext(msg);
      }
    }
  }

  public String getLinkId()
  {
    return link_id;
  }

  /**
   * Might be null, if we are the server and we haven't gotten a tip yet.
   * Might be lies, we just store whatever the other side sends.
   * Might change, as the other side updates what they send
   */
  public PeerInfo getPeerInfo()
  {
    return peer_info;
  }

}
