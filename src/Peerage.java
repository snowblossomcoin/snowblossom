package snowblossom;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.HashMap;
import snowblossom.proto.BlockSummary;
import snowblossom.proto.PeerMessage;
import snowblossom.proto.PeerChainTip;

import snowblossom.proto.Transaction;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;


import java.util.logging.Logger;
import java.util.logging.Level;

import java.util.ArrayList;
import snowblossom.proto.PeerInfo;
import snowblossom.proto.PeerList;

import com.google.protobuf.ByteString;

import java.util.Scanner;
import java.util.List;
import java.util.LinkedList;
import java.net.URL;
import java.util.Random;

/**
 * Joe: Should I class that handles communicating with a bunch of peers be called the Peerage?
 * Tyler: Gossiper
 * Joe: this isn't gossip, this is facts, sir
 * Tyler: that should be the entirety of the documentation on the class
 */
public class Peerage
{
  public static final long REFRESH_LEARN_TIME = 4L * 3600L * 1000L; //4hr
  public static final long SAVE_PEER_TIME = 300L * 1000L; //5min

  private static final Logger logger = Logger.getLogger("Peerage");

  private SnowBlossomNode node;

  private Map<String, PeerLink> links;

  private Map<String, PeerInfo> peer_rumor_list;

  private ImmutableSet<String> self_peer_names;
  
  public Peerage(SnowBlossomNode node)
  {
    this.node = node;

    links = new HashMap<>();
    peer_rumor_list = new HashMap<String,PeerInfo>();

    ByteString peer_data = node.getDB().getSpecialMap().get("peerlist");
    if (peer_data != null)
    {
      try
      {
        PeerList db_peer_list = PeerList.parseFrom(peer_data);
        for(PeerInfo info : db_peer_list.getPeersList())
        {
          learnPeer(info);
        }
        logger.info(String.format("Loaded %d peers from database", peer_rumor_list.size()));
        logger.info("Peers: " + peer_rumor_list.keySet());
      }
      catch(Exception e)
      {
        logger.log(Level.INFO, "Exception loading peer list", e);
      }
    }

    learnSelfAndSeed();

  }

  public void start()
  {
    logger.info("Starting peerage");
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
    PeerChainTip.Builder tip = PeerChainTip.newBuilder();
    BlockSummary summary = node.getBlockIngestor().getHead();

    tip.setNetworkName(node.getParams().getNetworkName());

    if (summary != null)
    {
      tip.setHeader(summary.getHeader());
    }
    TreeMap<Double, PeerInfo> peer_map = new TreeMap<>();
    Random rnd = new Random();
    synchronized(peer_rumor_list)
    {
      for(PeerInfo pi : peer_rumor_list.values())
      {
        peer_map.put(rnd.nextDouble(), pi);
      }
    }

    for(int i=0; i<8; i++)
    {
      if (peer_map.size() > 0)
      {
        tip.addPeers(peer_map.pollFirstEntry().getValue());
      }
    }
    
   
    return tip.build();
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
      try
      {
        link.writeMessage(PeerMessage.newBuilder().setTip(tip).build());
      }
      catch(Throwable e)
      {
        link.close();
      }
    }
  }

  public void broadcastTransaction(Transaction tx)
  {
    for(PeerLink link : getLinkList())
    {
      try
      {
        link.writeMessage(PeerMessage.newBuilder().setTx(tx).build());
      }
      catch(Throwable e)
      {
        link.close();
      }
    }
  }

  public void connectPeer(String host, int port)
  {
    new PeerClient(node, PeerInfo.newBuilder().setHost(host).setPort(port).build());
  }

  public void learnSelfAndSeed()
  {
    for(PeerInfo info : getSelfPeers())
    {
      learnPeer(info);
    }
    for(String s : node.getParams().getSeedNodes())
    {
      PeerInfo pi = PeerInfo.newBuilder()
        .setHost(s)
        .setPort(node.getParams().getDefaultPort())
        .setLearned(System.currentTimeMillis())
        .build();
      learnPeer(pi);
    }

  }

  public void learnPeer(PeerInfo info)
  {
    synchronized(peer_rumor_list)
    {
      String name = PeerUtil.getString(info);
      if (peer_rumor_list.containsKey(name))
      {
        PeerInfo n = PeerUtil.mergePeers(info, peer_rumor_list.get(name));
        peer_rumor_list.put(name, n);
      }
      else
      {
        peer_rumor_list.put(name, info);
      }
    }

  }

  public class PeerageMaintThread extends Thread
  {
    public PeerageMaintThread()
    {
      setName("PeerageMaintThread");
      setDaemon(true);
    }

    long last_learn_time = System.currentTimeMillis();
    long save_peer_time = System.currentTimeMillis();

    public void run()
    {
      while(true)
      {   
        try
        {
          Thread.sleep(10000);
          runPrune();
          sendAllTips();
          connectToPeers();

          if (last_learn_time + REFRESH_LEARN_TIME < System.currentTimeMillis())
          {
            last_learn_time = System.currentTimeMillis();
            learnSelfAndSeed();
          }
          if (save_peer_time + SAVE_PEER_TIME < System.currentTimeMillis())
          {
            save_peer_time = System.currentTimeMillis();
            PeerList.Builder peer_list = PeerList.newBuilder();
            synchronized(peer_rumor_list)
            {
              peer_list.addAllPeers(peer_rumor_list.values());
            }

            node.getDB().getSpecialMap().put("peerlist", peer_list.build().toByteString());
          }
        }
        catch(Throwable t)
        {
          logger.log(Level.WARNING, "PeerageMaintThread", t);
        }
      }
    }

    private void connectToPeers()
    {
      int connected = getLinkList().size();
      int desired = node.getConfig().getIntWithDefault("peer_count", 8);
      logger.log(Level.FINEST, String.format("Connected to %d, desired %d", connected, desired));
      if (desired <= connected)
      {
        return;
      }

      logger.log(Level.FINEST, "Looking for more peers to connect to");
      TreeSet<String> exclude_set = new TreeSet<>();
      exclude_set.addAll(self_peer_names);
      synchronized(links)
      {
        exclude_set.addAll(links.keySet());
      }

      ArrayList<PeerInfo> options = new ArrayList<>();
      synchronized(peer_rumor_list)
      {
        for(PeerInfo i : peer_rumor_list.values())
        {
          if (!exclude_set.contains(PeerUtil.getString(i)))
          {
            options.add(i);
          }
        } 
      }
      logger.log(Level.FINEST, String.format("There are %d peer options", options.size()));
      Random rnd = new Random();
      if (options.size() > 0)
      {
        int idx = rnd.nextInt(options.size());
        PeerInfo pi = options.get(idx);
        logger.log(Level.FINEST, String.format("Selected peer: " + PeerUtil.getString(pi)));
        new PeerClient(node, pi);
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

  private List<PeerInfo> getSelfPeers()
  {
    List<String> advertise_hosts= new LinkedList<>();

    List<PeerInfo> self_peers = new LinkedList<>();

    Set<String> self_names = new TreeSet<>();

    if (node.getConfig().isSet("service_port"))
    {

      try{
        String ipv4_host = getUrlLine("http://ipv4-lookup.snowblossom.org/myip");
        advertise_hosts.add(ipv4_host);
      }
      catch(Throwable t){}

      try{
        String ipv6_host = getUrlLine("http://ipv6-lookup.snowblossom.org/myip");
        advertise_hosts.add(ipv6_host);
      }
      catch(Throwable t){}

      int port = node.getConfig().getInt("service_port");
      for(String host : advertise_hosts)
      {
        PeerInfo pi = PeerInfo.newBuilder()
          .setHost(host)
          .setPort(port)
          .setLearned(System.currentTimeMillis())
          .build();

        self_peers.add(pi);

        self_names.add(PeerUtil.getString(pi));

      }

		}
    self_peer_names = ImmutableSet.copyOf(self_names);

    return self_peers;
		
  }


  private static String getUrlLine(String url)
    throws java.io.IOException
  {
    URL u = new URL(url);
    Scanner scan =new Scanner(u.openStream());
    String line = scan.nextLine();
    scan.close();
    return line;
  }
  

}
