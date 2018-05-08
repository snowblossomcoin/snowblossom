package snowblossom;

import java.util.Map;
import java.util.HashMap;
import snowblossom.proto.BlockSummary;
import snowblossom.proto.PeerMessage;
import snowblossom.proto.PeerChainTip;

import snowblossom.proto.Transaction;

import com.google.common.collect.ImmutableList;


import java.util.logging.Logger;
import java.util.logging.Level;

import java.util.ArrayList;
import snowblossom.proto.PeerInfo;
import snowblossom.proto.PeerList;

import com.google.protobuf.ByteString;


/**
 * Joe: Should I class that handles communicating with a bunch of peers be called the Peerage?
 * Tyler: Gossiper
 * Joe: this isn't gossip, this is facts, sir
 * Tyler: that should be the entirety of the documentation on the class
 */
public class Peerage
{
  private static final Logger logger = Logger.getLogger("Peerage");

  private SnowBlossomNode node;

  private Map<String, PeerLink> links;

  private ArrayList<PeerInfo> peer_rumor_list;
  
  public Peerage(SnowBlossomNode node)
  {
    this.node = node;

    links = new HashMap<>();
    peer_rumor_list = new ArrayList<PeerInfo>();

    ByteString peer_data = node.getDB().getSpecialMap().get("peerlist");
    if (peer_data != null)
    {
      try
      {
      PeerList db_peer_list = PeerList.parseFrom(peer_data);
      peer_rumor_list.addAll(db_peer_list.getPeersList());
      logger.info(String.format("Loaded %d peers from database", peer_rumor_list.size()));
      }
      catch(Exception e)
      {
        logger.log(Level.INFO, "Exception loading peer list", e);
      }
    }
  }

  public void start()
  {
    new PeerageMaintThread().start();
  }

  public void register(PeerLink link)
  {
    synchronized(links)
    {
      links.put(link.getLinkId(), link);
    }

    link.writeMessage(PeerMessage.newBuilder().setTip(getTip()).build());
  }

  private PeerChainTip getTip()
  {
    PeerChainTip tip;
    BlockSummary summary = node.getBlockIngestor().getHead();
    if (summary != null)
    {
      tip = PeerChainTip.newBuilder()
        .setHeader(summary.getHeader())
        .setNetworkName(node.getParams().getNetworkName())
        .build();
    }
    else
    {

      tip = PeerChainTip.newBuilder()
        .setNetworkName(node.getParams().getNetworkName())
        .build();
    }
    return tip;
  }

  private ImmutableList<PeerLink> getLinkList()
  {
    synchronized(links)
    {
      return ImmutableList.copyOf(links.values());
    }
  }

  public void sendAllTips()
  {
    PeerChainTip tip = getTip();
    for(PeerLink link : getLinkList())
    {
      link.writeMessage(PeerMessage.newBuilder().setTip(tip).build());
    }
  }

  public void broadcastTransaction(Transaction tx)
  {
    for(PeerLink link : getLinkList())
    {
      link.writeMessage(PeerMessage.newBuilder().setTx(tx).build());
    }
  }

  public void connectPeer(String host, int port)
  {
    new PeerClient(node, host, port);
  }

  public class PeerageMaintThread extends Thread
  {
    public PeerageMaintThread()
    {
      setName("PeerageMaintThread");
      setDaemon(true);
    }

    public void run()
    {
      while(true)
      {   
        try
        {
          Thread.sleep(10000);
          runPrune();
          sendAllTips();


        }
        catch(Throwable t)
        {
          logger.log(Level.WARNING, "PeerageMaintThread", t);
        }
      }
    }

    private void runPrune()
    {
      for(PeerLink link : getLinkList())
      {
        if (!link.isOpen())
        {
          synchronized(links)
          {
            links.remove(link.getLinkId());
          }
        }
      }
    }

  }

}
