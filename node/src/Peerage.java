package snowblossom.node;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.protobuf.ByteString;
import duckutil.AtomicFileOutputStream;
import duckutil.ExpiringLRUCache;
import duckutil.NetUtil;
import duckutil.PeriodicThread;
import java.io.PrintStream;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.lib.tls.MsgSigUtil;
import snowblossom.proto.*;

/**
 * Joe: Should I class that handles communicating with a bunch of peers be called the Peerage?
 * Tyler: Gossiper
 * Joe: this isn't gossip, this is facts, sir
 * Tyler: that should be the entirety of the documentation on the class
 */
public class Peerage
{
  public static final long REFRESH_LEARN_TIME = 3600L * 1000L; // 1hr
  public static final long SAVE_PEER_TIME = 60L * 1000L; // 1min
  public static final long PEER_EXPIRE_TIME = 3L * 86400L * 1000L; // 3 days
  public static final long RANDOM_CLOSE_TIME = 2L* 3600L * 1000L; // 2 hours

  /** min time before trying to reconnect to a specific port:host */
  public static final long RECONNECT_TIME = 300L * 1000L; //5 min

  private static final Logger logger = Logger.getLogger("snowblossom.peering");

  private SnowBlossomNode node;

  private Map<String, PeerLink> links;
  private Map<String, PeerInfo> peer_rumor_list;
  private ImmutableList<PeerInfo> self_peer_info;

  private volatile BlockHeader highest_seen_header;
  private long last_random_close = System.currentTimeMillis();

  private final int desired_peer_count;
  private final int desired_interest_peer_count;
  private final int desired_trust_peer_count;

  /** Cache for RECONNECT_TIME */
  private ExpiringLRUCache<String, Boolean> connect_attempt_cache = new ExpiringLRUCache<>(1000, RECONNECT_TIME);

  public Peerage(SnowBlossomNode node)
  {
    this.node = node;

    this.desired_peer_count = node.getConfig().getIntWithDefault("peer_count", 8);
    this.desired_interest_peer_count = node.getConfig().getIntWithDefault("interest_peer_count", 4);
    this.desired_trust_peer_count = node.getConfig().getIntWithDefault("trust_peer_count", 4);


    links = new HashMap<>();

    peer_rumor_list = new HashMap<String,PeerInfo>();

    ByteString peer_data = node.getDB().getSpecialMap().get("peerlist");
    if (peer_data != null)
    {
      try
      {
        PeerList db_peer_list = PeerList.parseFrom(peer_data);
        logger.info(String.format("Peer database size: %d bytes, %d entries", peer_data.size(), db_peer_list.getPeersCount()));
        for(PeerInfo info : db_peer_list.getPeersList())
        {
          if (PeerUtil.isSane(info, node.getParams()))
          {
            learnPeer(info, true);
          }
          else
          {
            logger.warning("Not sane peer: " + info);
          }
        }
        logger.info(String.format("Loaded %d peers from database", peer_rumor_list.size()));

        if (node.getConfig().isSet("peer_log"))
        {
          String log_path = node.getConfig().get("peer_log");
          PrintStream peer_out = new PrintStream(new AtomicFileOutputStream(log_path));
          for(PeerInfo info : db_peer_list.getPeersList())
          {
            peer_out.println(info.toString());
          }

          peer_out.close();
        }


        logger.log(Level.FINER, "Peers: " + peer_rumor_list.keySet());
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

    link.writeMessage(PeerMessage.newBuilder().setTip(getTip(0)).build());
  }

  private PeerTipInfo getTipInfo(int shard_id)
  {
    PeerTipInfo.Builder tip_info = PeerTipInfo.newBuilder();

    Set<ChainHash> block_set = new HashSet<>();

    if (Dancer.isCoordinator(shard_id))
    {
      BlockHeader coord_head = node.getForgeInfo().getShardHead(shard_id);
      if (coord_head != null)
      {
        BlockPreview bp = BlockchainUtil.getPreview(coord_head);
        tip_info.setCoordHead(bp);
      }
    }

    for(BlockHeader bh : node.getForgeInfo().getNetworkActiveShards().values())
    {
      // Find the coordinators that we know about
      if (Dancer.isCoordinator( bh.getShardId() ))
      {
          logger.fine("Coordinator seems to be: " + bh.getShardId());
          Map<Integer, BlockHeader> import_map =
            node.getForgeInfo().getImportedShardHeads(bh, node.getParams().getMaxShardSkewHeight()+2);

          // Start from what this coordinator knows about this shard
          // and include them here
          if (import_map.containsKey(shard_id))
          {
            BlockHeader start = import_map.get(shard_id);
            block_set.addAll(node.getForgeInfo().climb(new ChainHash(start.getSnowHash()),
              -1, node.getParams().getMaxShardSkewHeight()*2));
          }
      }
    }

    { // Add things around this head

      BlockSummary head = node.getBlockIngestor(shard_id).getHead();

      block_set.addAll(
        node.getForgeInfo().getBlocksAround(
          new ChainHash(head.getHeader().getSnowHash()),
          node.getParams().getMaxShardSkewHeight()+2,
          -1
        )
      );
    }
    logger.fine("Block set: " + block_set.size());

    for(ChainHash h : block_set)
    {
      BlockHeader head = node.getForgeInfo().getHeader(h);
      if (head != null)
      {
        BlockPreview bp = BlockchainUtil.getPreview(head);
        tip_info.addPreviews(bp);
      }
    }

    return tip_info.build();
  }

  private PeerChainTip getTip(int shard_id)
  {
    PeerChainTip.Builder tip = PeerChainTip.newBuilder();
    BlockSummary summary = node.getBlockIngestor(shard_id).getHead();

    tip.setNetworkName(node.getParams().getNetworkName());
    tip.setVersion(Globals.VERSION);

    if (summary != null)
    {
      tip.setHeader(summary.getHeader());
      if (node.getTrustnetAddress() != null)
      {
        try
        {
          SignedMessagePayload payload = SignedMessagePayload.newBuilder()
            .setPeerTipInfo( getTipInfo(shard_id) )
            .build();

          WalletKeyPair wkp = node.getTrustnetWalletDb().getKeys(0);
          AddressSpec claim = node.getTrustnetWalletDb().getAddresses(0);
          tip.setSignedHead( MsgSigUtil.signMessage(claim, wkp, payload));
        }
        catch(ValidationException e)
        {
          logger.log(Level.WARNING, "getTip", e);
        }
      }
    }

    LinkedList<PeerInfo> shuffled_peer_list = new LinkedList<>();

    synchronized(peer_rumor_list)
    {
      shuffled_peer_list.addAll(peer_rumor_list.values());
    }
    Collections.shuffle(shuffled_peer_list);

    if (self_peer_info != null)
    {
      // Always add self first
      tip.addAllPeers(self_peer_info);
    }
    for(int i=0; i<10; i++)
    {
      if (shuffled_peer_list.size() > 0)
      {
        tip.addPeers(shuffled_peer_list.poll());
      }
    }

    return tip.build();
  }

  public int getConnectedPeerCount()
  {
    synchronized(links)
    {
      return links.size();
    }
  }

  public int getEstimateUniqueNodes()
  {
    HashSet<ByteString> set = new HashSet<>();
    synchronized(peer_rumor_list)
    {
      for(PeerInfo info : peer_rumor_list.values())
      {
        if (info.getNodeId().size() > 0)
        {
          set.add(info.getNodeId());
        }
      }
    }
    return set.size();

  }

  public Map<String, Integer> getVersionMap()
  {
    HashMap<ByteString, String> ver_map = new HashMap<>();
    synchronized(peer_rumor_list)
    {
      for(PeerInfo info : peer_rumor_list.values())
      {
        String ver = info.getVersion();
        ByteString node_id = info.getNodeId();
        if (info.getNodeId().size() > 0)
        {
          if (!ver_map.containsKey(node_id))
          {
            ver_map.put(node_id, ver);
          }
          else
          {
            String v2 = ver_map.get(node_id);
            if (ver.compareTo(v2) > 0)
            {
              ver_map.put(node_id, ver);
            }
          }
        }
      }
    }

    TreeMap<String, Integer> map = new TreeMap<>();
    for(String ver : ver_map.values())
    {
      if (!map.containsKey(ver))
      {
        map.put(ver, 0);
      }
      map.put(ver, map.get(ver) + 1);
    }

    return map;
  }

  public ImmutableList<PeerLink> getLinkList()
  {
    synchronized(links)
    {
      return ImmutableList.copyOf(links.values());
    }
  }

  public void setHighestHeader(BlockHeader header)
  {
    if (highest_seen_header == null) highest_seen_header = header;

    if (highest_seen_header.getBlockHeight() < header.getBlockHeight())
    {
      highest_seen_header = header;
    }
  }
  public BlockHeader getHighestSeenHeader()
  {
    return highest_seen_header;
  }

  /** Sends tips related to a particular shard_id */
  public void sendAllTips(int shard_id)
  {
    PeerChainTip tip = getTip(shard_id);
    logger.fine(String.format("Sending tip on shard %d - s:%d h:%d",
      shard_id,
      tip.getHeader().getShardId(),
      tip.getHeader().getBlockHeight()));

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

  public void closeAll()
  {
    for(PeerLink link : getLinkList())
    {
      link.close();
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
    throws Exception
  {
    new PeerClient(node, PeerInfo.newBuilder().setHost(host).setPort(port).build());
  }

  public void learnSelfAndSeed()
  {
    for(PeerInfo info : getSelfPeers())
    {
      learnPeer(info);
    }

    if(node.getConfig().isSet("seed_uris"))
    for(String uri : node.getConfig().getList("seed_uris"))
    {
      PeerInfo pi_uri = PeerUtil.getPeerInfoFromUri(uri, node.getParams());

      if(pi_uri != null)
      {
        PeerInfo pi = PeerInfo.newBuilder()
          .mergeFrom(pi_uri)
          .setVersion("seed")
          .setLearned(System.currentTimeMillis() - PEER_EXPIRE_TIME + REFRESH_LEARN_TIME)
          .build();
        learnPeer(pi, true);
      }
    }
    for(String s : node.getParams().getSeedNodes())
    {
      try
      {
        for(InetAddress ia : InetAddress.getAllByName(s))
        {
          String address = ia.getHostAddress();

          // Make this seed peer entry as just about to expire
          // so when we learn the gossip one from the real peer, that takes precedence
          PeerInfo pi = PeerInfo.newBuilder()
            .setHost(address)
            .setPort(node.getParams().getDefaultPort())
            .setLearned(System.currentTimeMillis() - PEER_EXPIRE_TIME + REFRESH_LEARN_TIME)
            .setVersion("seed")
            .build();
          learnPeer(pi, true);
        }
       }
      catch(Exception e)
      {
        logger.info(String.format("Exception resolving %s - %s", s, e.toString()));
      }
    }
    if (node.getParams().getNetworkName().equals("snowblossom"))
    {
      PeerInfo pi = PeerInfo.newBuilder()
        .setHost("snow-tx1.snowblossom.org")
        .setPort(443)
        .setLearned(System.currentTimeMillis() - PEER_EXPIRE_TIME + REFRESH_LEARN_TIME)
        .setVersion("seed")
        .build();
      learnPeer(pi, true);
    }


  }

  public void learnPeer(PeerInfo info)
  {
    learnPeer(info, false);
  }
  public void learnPeer(PeerInfo info, boolean override_expiration)
  {
    if (!override_expiration)
    {
      if (info.getLearned() + PEER_EXPIRE_TIME < System.currentTimeMillis()) return;
    }
    if (!PeerUtil.isSane(info, node.getParams())) return;

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

  private volatile boolean gotFirstTip=false;
  public void reportTip()
  {
    if (!gotFirstTip)
    {
      gotFirstTip=true;
      logger.info("Got first tip from a remote peer");

    }

  }

  public class PeerageMaintThread extends PeriodicThread
  {
    public PeerageMaintThread()
    {
      super(12000);
      setName("PeerageMaintThread");
      setDaemon(true);
    }

    long last_learn_time = 0L;
    long save_peer_time = System.currentTimeMillis();

    @Override
    public void runPass()
      throws Exception
    {

      logger.fine("Prune Links");
      pruneLinks();
      logger.fine("Connect to peers");
      connectToPeers();
      Random rnd = new Random();
      // TODO - don't send all tips all the time
      // might want to do a rotation through shards
      ArrayList<Integer> my_shards = new ArrayList<>();
      my_shards.addAll(node.getActiveShards());

      Collections.shuffle(my_shards);
      int sent_count=0;
      for(int s : my_shards)
      {
        logger.fine("Send tips: " + s);
        sendAllTips(s);
        sent_count++;
        if (sent_count > 16) break;
      }


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



    private void connectToPeers()
    {
      // Existing method is to just try to make sure to have peer_count links
      // new method will do the following:
      // For shards we are interested in, try to have at least SHARD_INTEREST_PEER_COUNT links for each shard.
      // For shards we are not interested in, if we have a trust address set, have at least TRUST_PEER_COUNT links for each shard
      // Have at least peer_count links
      ConnectionReport cr = getConnectionReport();

      logger.fine(cr.toString());

      int connected = getLinkList().size();
      int desired = node.getConfig().getIntWithDefault("peer_count", 8);
      logger.log(Level.FINE, String.format("Connected to %d, desired %d", connected, desired));
      if (connected > 4)
      {
        // The only constant is change
        // Sometimes we want to just change things up
        if (last_random_close + RANDOM_CLOSE_TIME < System.currentTimeMillis())
        {
          LinkedList<PeerLink> lst = new LinkedList<PeerLink>();
          lst.addAll(getLinkList());
          Collections.shuffle(lst);
          if (lst.size() > 0)
          {
            logger.log(Level.FINE, "Closing a random link ");
            lst.poll().close();
          }
          last_random_close = System.currentTimeMillis();
        }
      }
      if (desired <= connected)
      {
        // Only forget about saved peer data
        // if we have managed some connections
        // this allows us to have been off for weeks or months and not purge
        // the peers data until we get some new connections
        pruneExpiredPeers();
      }

      HashSet<ByteString> exclude_set = new HashSet<>();
      exclude_set.add(getNodeId());
      exclude_set.addAll( cr.getConnectedIds().keySet() );

      TreeMap<Double, PeerInfo> util_map = new TreeMap<>();

      synchronized(peer_rumor_list)
      {
        for(PeerInfo i : peer_rumor_list.values())
        {
          if (!exclude_set.contains(i.getNodeId()))
          {
            double val = cr.getUtilityScore(i);
            synchronized(connect_attempt_cache)
            {
              if (connect_attempt_cache.get(PeerUtil.getString(i))==null)
              {
                util_map.put(val, i);
              }
            }
          }
        }
      }

      HashSet<ByteString> connect_start = new HashSet<>();
      logger.fine("Map of possible connections: " + util_map.size());

      int max_add=4;
      for(int att = 0; att < max_add; att++)
      {
        if (util_map.size() > 0)
        {
          Map.Entry<Double, PeerInfo> entry = util_map.pollLastEntry();
          PeerInfo pi = entry.getValue();
          if (entry.getKey() > 1.0)
          if (!connect_start.contains(pi.getNodeId()))
          {
            synchronized(connect_attempt_cache)
            {
              connect_attempt_cache.put(PeerUtil.getString(pi), true);
            }
            connect_start.add(pi.getNodeId());

            DecimalFormat df = new DecimalFormat("0.000");
            logger.info(String.format("Attempting connection to peer (%s) with val (%s)",
              PeerUtil.getString(pi),
              df.format(entry.getKey())));

            try
            {
              new PeerClient(node, pi);
            }
            catch(Exception e)
            {
              logger.log(Level.INFO, "Error with peer: " + PeerUtil.getString(pi), e);
            }
          }
        }
      }

    }

    private void pruneLinks()
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
    public void pruneExpiredPeers()
    {
      synchronized(peer_rumor_list)
      {
        HashSet<String> to_remove = new HashSet<>();
        for(Map.Entry<String, PeerInfo> me : peer_rumor_list.entrySet())
        {
          PeerInfo info = me.getValue();

          if (info.getLearned() + PEER_EXPIRE_TIME < System.currentTimeMillis())
          {
            to_remove.add(me.getKey());
          }

        }

        for(String key : to_remove)
        {
          peer_rumor_list.remove(key);
        }

      }
    }
  }

  private ByteString internal_node_id;
  private synchronized ByteString getNodeId()
  {

    // If we use the DB, then nodes will have the same ID if the DB
    // is cloned for whatever reason
    //ByteString id = node.getDB().getSpecialMap().get("node_id");
    ByteString id = internal_node_id;
    if (id == null)
    {
      Random rnd = new Random();
      byte[] b = new byte[Globals.MAX_NODE_ID_SIZE];
      rnd.nextBytes(b);
      id = ByteString.copyFrom(b);
      node.getDB().getSpecialMap().put("node_id", id);
      internal_node_id = id;
    }

    return id;
  }

  private List<PeerInfo> getSelfPeers()
  {
    List<String> advertise_hosts= new LinkedList<>();

    List<PeerInfo> self_peers = new LinkedList<>();


    try{
      String ipv4_host = NetUtil.getUrlLine("http://ipv4-lookup.snowblossom.org/myip");
      advertise_hosts.add(ipv4_host);
    }
    catch(Throwable t){}

    try{
      String ipv6_host = NetUtil.getUrlLine("http://ipv6-lookup.snowblossom.org/myip");
      advertise_hosts.add(ipv6_host);
    }
    catch(Throwable t){}

    if (node.getServicePorts() != null)
    {
      for(int port : node.getServicePorts())
      {
        ByteString node_id = getNodeId();

        for(String host : advertise_hosts)
        {
          PeerInfo.Builder pi = PeerInfo.newBuilder()
            .setHost(host)
            .setPort(port)
            .setLearned(System.currentTimeMillis())
            .setVersion(Globals.VERSION)
            .setNodeId(node_id)
            .setConnectionType(PeerInfo.ConnectionType.GRPC_TCP)
            .addAllShardIdSet( node.getInterestShards() );


          if (node.getTrustnetAddress() != null)
          {
            pi.setTrustnetAddress(node.getTrustnetAddress().getBytes());
            addTrustnetSigned(pi);
          }
         self_peers.add(pi.build());
        }
      }
    }
    if (node.getTlsServicePorts() != null)
    {
      for(int port : node.getTlsServicePorts())
      {
        ByteString node_id = getNodeId();

        for(String host : advertise_hosts)
        {
          PeerInfo.Builder pi = PeerInfo.newBuilder()
            .setHost(host)
            .setPort(port)
            .setLearned(System.currentTimeMillis())
            .setVersion(Globals.VERSION)
            .setNodeId(node_id)
            .setConnectionType(PeerInfo.ConnectionType.GRPC_TLS)
            .setNodeSnowAddress(node.getTlsAddress().getBytes())
            .addAllShardIdSet( node.getInterestShards() );

          if (node.getTrustnetAddress() != null)
          {
            pi.setTrustnetAddress(node.getTrustnetAddress().getBytes());
            addTrustnetSigned(pi);
          }
          self_peers.add(pi.build());
        }
      }
    }

    self_peer_info = ImmutableList.copyOf(self_peers);

    return self_peers;

  }

  private void addTrustnetSigned(PeerInfo.Builder pi)
  {
    SignedMessagePayload sign_payload = SignedMessagePayload.newBuilder().setPeerInfo(pi.build()).build();

    WalletKeyPair wkp = node.getTrustnetWalletDb().getKeys(0);
    AddressSpec claim = node.getTrustnetWalletDb().getAddresses(0);
    try
    {
      pi.setTrustnetSignedPeerInfo( MsgSigUtil.signMessage(claim, wkp, sign_payload));
    }
    catch(ValidationException e)
    {
      throw new RuntimeException(e);
    }
  }

  public ConnectionReport getConnectionReport()
  {
    ConnectionReport cr = new ConnectionReport();
    synchronized(links)
    {
      for(PeerLink pl : links.values())
      {
        cr.addPeerInfo(pl.getPeerInfo());
      }
    }

    return cr;
  }


  public class ConnectionReport
  {
    // What peers we are connected to
    HashMap<ByteString, PeerInfo> connected_ids;

    // What peers we have in our trust network for each shard
    SetMultimap<Integer, ByteString> trust_network_map;

    // What peers we have that are interested in each shard
    SetMultimap<Integer, ByteString> interest_network_map;

    public ConnectionReport()
    {
      connected_ids = new HashMap<>();
      trust_network_map = MultimapBuilder.treeKeys().hashSetValues().build();
      interest_network_map = MultimapBuilder.treeKeys().hashSetValues().build();
    }

    public synchronized Map<ByteString, PeerInfo> getConnectedIds()
    {
      return ImmutableMap.copyOf(connected_ids);
    }

    /**
     * Get the utility score of adding this peer
     * - 1 point if we are below the desired_peer_count
     * - 1 point for each shard below desired_interest_peer_count
     * - 1 point for each shard below desired_trust_peer_count
     */
    public synchronized double getUtilityScore(PeerInfo pi)
    {
      Random rnd = new Random();
      double val = rnd.nextDouble() / 1e3;

      if (connected_ids.size() < desired_peer_count) val += 1.0;

      Set<Integer> interest = node.getInterestShards();

      for(int shard : pi.getShardIdSetList())
      {
        if (interest.contains(shard))
        {
          if (interest_network_map.get(shard).size() < desired_interest_peer_count)
          {
            val += 1.0;
          }
        }
        else
        {
          if (pi.getTrustnetAddress().size() > 0)
          {
            AddressSpecHash trust_addr = new AddressSpecHash(pi.getTrustnetAddress());
            if (node.getShardUtxoImport().getTrustedSigner().contains(trust_addr))
            {
              if (trust_network_map.get(shard).size() < desired_trust_peer_count)
              {
                val += 1.0;
              }
            }
          }
        }
      }

      return val;

    }


    public synchronized void addPeerInfo(PeerInfo pi)
    {
      if (pi == null) return;

      // Has a node id
      if (pi.getNodeId().size() == 0) return;

      // Not self
      if (pi.getNodeId().equals(getNodeId())) return;

      ByteString node_id = pi.getNodeId();

      connected_ids.put(node_id, pi);

      for(int shard : pi.getShardIdSetList())
      {
        interest_network_map.put(shard, node_id);
      }

      if (pi.getTrustnetAddress().size() > 0)
      {
        AddressSpecHash trust_addr = new AddressSpecHash(pi.getTrustnetAddress());
        if (node.getShardUtxoImport().getTrustedSigner().contains(trust_addr))
        {
          for(int shard : pi.getShardIdSetList())
          {
            trust_network_map.put(shard, node_id);
          }
        }
      }
    }

    public String toString()
    {
      StringBuilder sb = new StringBuilder();
      sb.append("ConnectionReport{");

      sb.append("nodes:" + connected_ids.size());

      Set<Integer> interest = node.getInterestShards();
      boolean has_trust = (node.getShardUtxoImport().getTrustedSigner().size() > 0);

      for(int i=0; i<=node.getParams().getMaxShardId(); i++)
      {
        if (interest.contains(i))
        {
          sb.append(",i" + i + "=" + interest_network_map.get(i).size());
        }
        else
        {
          if (has_trust)
          {
            sb.append(",e" + i + "=" + trust_network_map.get(i).size());
          }

        }


      }

      sb.append("}");

      return sb.toString();

    }

  }

}
