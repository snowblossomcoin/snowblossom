package snowblossom.node;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import snowblossom.proto.*;
import snowblossom.lib.*;

import java.util.Random;
import java.util.TreeMap;
import java.util.List;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

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
  private PeerInfo peer_info; //only set when we are client

  private TreeMap<Integer, ChainHash> peer_block_map = new TreeMap<Integer, ChainHash>();


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
    try
    {
      msg = PeerMessage.parseFrom(msg.toByteString());
      if (msg.hasTx())
      {
        Transaction tx = msg.getTx();
        //logger.info("TX: " + new ChainHash(tx.getTxHash()));
        try
        {
          if (node.getMemPool().addTransaction(tx))
          {
            node.getPeerage().broadcastTransaction(tx);
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
        PeerChainTip tip = msg.getTip();
        if (!node.getParams().getNetworkName().equals(tip.getNetworkName()))
        {
          logger.log(Level.FINE, String.format("Peer has wrong name: %s", tip.getNetworkName()));
          close();
          return;
        }
        node.getPeerage().reportTip();

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
          considerBlockHeader(header);
          node.getPeerage().setHighestHeader(header);
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
        // Other side is asking for a block
        ChainHash hash = new ChainHash(msg.getReqBlock().getBlockHash());
        Block blk = node.getDB().getBlockMap().get(hash.getBytes());
        if (blk != null)
        {
          writeMessage( PeerMessage.newBuilder().setBlock(blk).build() );
        }
      }
      else if (msg.hasBlock())
      {
        // Getting a block, we probably asked for it.  See if we can eat it.
        Block blk = msg.getBlock();
        try
        {
          if (node.getBlockIngestor().ingestBlock(blk))
          { // we could eat it, think about getting more blocks
            int next = blk.getHeader().getBlockHeight()+1;
            synchronized(peer_block_map)
            {
              if (peer_block_map.containsKey(next))
              {
                ChainHash target = peer_block_map.get(next);

                if (node.getBlockIngestor().reserveBlock(target))
                {
                  writeMessage( PeerMessage.newBuilder()
                    .setReqBlock(
                      RequestBlock.newBuilder().setBlockHash(target.getBytes()).build())
                    .build());
                }
              }
            }
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
        // Peer is asking for a block header
        int height = msg.getReqHeader().getBlockHeight();
        ChainHash hash = node.getDB().getBlockHashAtHeight(height);
        if (hash != null)
        {
          BlockSummary summary = node.getDB().getBlockSummaryMap().get(hash.getBytes());
          writeMessage( PeerMessage.newBuilder().setHeader(summary.getHeader()).build() );
        }
      }
      else if (msg.hasHeader())
      {
        // We got a header, probably one we asked for
        BlockHeader header = msg.getHeader();
        Validation.checkBlockHeaderBasics(node.getParams(), header, false);
        considerBlockHeader(header);
      }
      else if (msg.hasReqCluster())
      {
        ChainHash tx_id = new ChainHash(msg.getReqCluster().getTxHash());
        sendCluster(tx_id);
      }
    }
    catch(ValidationException e)
    {
      logger.log(Level.INFO, "Some validation error from " + getLinkId(), e);
    }
    catch(Throwable e)
    {
      logger.log(Level.INFO, "Some bs from " + getLinkId(), e);
      close();
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

  /**
   * The basic plan is, keep asking about previous blocks
   * until we get to one we have heard of.  Then we start requesting the blocks.
   */
  private void considerBlockHeader(BlockHeader header)
  {

    synchronized(peer_block_map)
    {
      peer_block_map.put(header.getBlockHeight(), new ChainHash(header.getSnowHash()));
    }

    // if we don't have this block
    if (node.getDB().getBlockSummaryMap().get(header.getSnowHash())==null)
    {
      int height = header.getBlockHeight();
      if ((height == 0) || (node.getDB().getBlockSummaryMap().get(header.getPrevBlockHash())!=null))
      { // but we have the prev block - get this block 
        if (node.getBlockIngestor().reserveBlock(new ChainHash(header.getSnowHash())))
        {
          writeMessage( PeerMessage.newBuilder()
            .setReqBlock(
              RequestBlock.newBuilder().setBlockHash(header.getSnowHash()).build())
            .build());
        }
      }
      else
      { //get more headers, still in the woods
        int next = header.getBlockHeight();
        if (node.getBlockIngestor().getHeight() + Globals.BLOCK_CHUNK_HEADER_DOWNLOAD_SIZE < next)
        {
          next = node.getBlockIngestor().getHeight() + Globals.BLOCK_CHUNK_HEADER_DOWNLOAD_SIZE;
        }
        while(peer_block_map.containsKey(next))
        {
          next--;
        }
        
        if (next >= 0)
        {
          ChainHash prev = new ChainHash(header.getPrevBlockHash());
          synchronized(peer_block_map)
          {
            if (peer_block_map.containsKey(next))
            {
              if (peer_block_map.get(next).equals(prev)) return;
            }
          }

          writeMessage( PeerMessage.newBuilder()
            .setReqHeader(
              RequestBlockHeader.newBuilder().setBlockHeight(next).build())
            .build());
        }
      }

    }

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
        channel.awaitTermination(3, TimeUnit.SECONDS);
      }
    }
    catch(Throwable e){}
  }

  public boolean isOpen()
  {
    if (last_received_message_time + 120000L < System.currentTimeMillis())
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

}
