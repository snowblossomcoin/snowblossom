package snowblossom;

import java.util.Map;
import java.util.HashMap;
import snowblossom.proto.BlockSummary;
import snowblossom.proto.PeerMessage;
import snowblossom.proto.PeerChainTip;

import snowblossom.proto.Transaction;

import com.google.common.collect.ImmutableList;

/**
 * Joe: Should I class that handles communicating with a bunch of peers be called the Peerage?
 * Tyler: Gossiper
 * Joe: this isn't gossip, this is facts, sir
 * Tyler: that should be the entirety of the documentation on the class
 */
public class Peerage
{
  private SnowBlossomNode node;

  private Map<String, PeerLink> links;
  
  public Peerage(SnowBlossomNode node)
  {
    this.node = node;

    links = new HashMap<>();
  }

  public void register(PeerLink link)
  {
    synchronized(links)
    {
      links.put(link.getLinkId(), link);
    }

    sendTip(link, getTip());
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

  private static void sendTip(PeerLink link, PeerChainTip tip)
  {
    link.writeMessage(PeerMessage.newBuilder().setTip(tip).build());
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
      sendTip(link, tip);
    }
  }

  public void broadcastTransaction(Transaction tx)
  {
    for(PeerLink link : getLinkList())
    {
      link.writeMessage(PeerMessage.newBuilder().setTx(tx).build());
    }
  }

}
