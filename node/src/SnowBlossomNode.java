package snowblossom.node;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import duckutil.Config;
import duckutil.ConfigFile;
import duckutil.ConfigMem;
import duckutil.MetricLogger;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.client.WalletUtil;
import snowblossom.lib.*;
import snowblossom.lib.SystemUtil;
import snowblossom.lib.db.DB;
import snowblossom.lib.db.atomicfile.AtomicFileDB;
import snowblossom.lib.db.lobstack.LobstackDB;
import snowblossom.lib.db.rocksdb.JRocksDB;
import snowblossom.lib.tls.CertGen;
import snowblossom.lib.trie.HashedTrie;
import snowblossom.proto.BlockHeader;
import snowblossom.proto.WalletDatabase;

public class SnowBlossomNode
{
  private static final Logger logger = Logger.getLogger("snowblossom.node");
  public static void main(String args[]) throws Exception
  {
    Globals.addCryptoProvider();

    if (args.length != 1)
    {
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: SnowBlossomNode <config_file>");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0]);

    LogSetup.setup(config);
    //LogSetup.listLoggers();

    new SnowBlossomNode(config);
    while(true)
    {
      Thread.sleep(2500);
      LogSetup.fixLevels();
      //LogSetup.listLoggers();
    }
  }

  private Config config;
  private SnowUserService user_service;
  private SnowPeerService peer_service;
  private DB db;
  private NetworkParams params;
  private Peerage peerage;
  private ShardBlockForge shard_blockforge;
  private ForgeInfo forge_info;
  private ShardUtxoImport shard_utxo_import;
  private MetaMemPool meta_mem_pool;
  private WalletDatabase trustnet_wallet_db;
  private TxBroadcaster tx_broadcaster;
  private DBMaintThread db_maint_thread;

  private ImmutableList<Integer> service_ports;
  private ImmutableList<Integer> tls_service_ports;
  private AddressSpecHash node_tls_address;
  private ImmutableList<StatusInterface> status_list;
  private ImmutableSet<Integer> shard_interest_set;
  private ImmutableSet<Integer> shard_config_set;
  private ImmutableMap<Integer, ShardComponents> shard_comps = ImmutableMap.of();

  private volatile boolean terminate;

  public SnowBlossomNode(Config config)
    throws Exception
  {
    this(config, ImmutableList.of(new StatusLogger()));

  }

  public SnowBlossomNode(Config config, List<StatusInterface> status_list)
    throws Exception
  {
    this.config = config;
    this.status_list = ImmutableList.copyOf(status_list);
    setStatus("Initializing SnowBlossomNode");

    if (config.isSet("metric_log"))
    {
      MetricLogger.init(config.get("metric_log"));
    }

    config.require("db_type");
    logger.info(String.format("Starting SnowBlossomNode version %s", Globals.VERSION));

    setupProfiler();
    setupParams();
    loadDB();
    loadWidgets();
    openShards();

    startServices();

    startWidgets();

    setStatus("SnowBlossomNode started");
  }

  public void stop()
  {
    terminate=true;
  }

  private void setupProfiler()
    throws Exception
  {
    if (config.isSet("profiler_log"))
    {
      long time = config.getLongWithDefault("profiler_period",20000);
      new Profiler(time, new PrintStream(new FileOutputStream( config.get("profiler_log"), true) )).start();
    }

  }


  private void setupParams()
  {
    params = NetworkParams.loadFromConfig(config);

    TreeSet<Integer> cover_set = new TreeSet<>();
    TreeSet<Integer> config_set = new TreeSet<>();

    if (config.isSet("shards"))
    {
      for(String shard_str : config.getList("shards"))
      {
        int shard_id = Integer.parseInt(shard_str);

        config_set.add(shard_id);

        // Add this shard and all shards down from it
        cover_set.addAll( ShardUtil.getCoverSet(shard_id, params) );

        // We need to trace all parents in order to get the entry point
        // into this shard
        cover_set.addAll( ShardUtil.getAllParents(shard_id) );
      }
    }
    else
    {
      cover_set.addAll( ShardUtil.getCoverSet(0, params) );
      config_set.add(0);
    }
    logger.info("Shard interest set: " + cover_set);

    shard_interest_set = ImmutableSet.copyOf(cover_set);
    shard_config_set = ImmutableSet.copyOf(config_set);

  }

  public void setStatus(String status)
  {
    for(StatusInterface si : status_list)
    {
      si.setStatus(status);
    }
  }

  private void loadWidgets()
    throws Exception
  {
    trustnet_wallet_db = loadWalletFromConfig("trustnet_key_path");
    if (trustnet_wallet_db != null)
    {
      AddressSpecHash trust_addr = getTrustnetAddress();
      logger.info("Trustnet signing key: " + AddressUtil.getAddressString("node", trust_addr) );
    }

    peerage = new Peerage(this);
    tx_broadcaster = new TxBroadcaster(peerage);
    forge_info = new ForgeInfo(this);
    shard_utxo_import = new ShardUtxoImport(this);
    shard_blockforge = new ShardBlockForge(this);
    meta_mem_pool = new MetaMemPool(this);
    db_maint_thread = new DBMaintThread(this);

  }

  /**
   * open shards where we know about a head
   */
  private void openShards()
    throws Exception
  {
    for(int shard_id : shard_interest_set)
    {
      // if we are interested and have a head for it, open it up
      if ( new BlockIngestor(this, shard_id).getHead() != null)
      {
        openShard(shard_id);
      }

    }

    // If we have nothing, open shard zero regardless
    // probably always want zero regardless, but whatever
    if(shard_comps.size() == 0) openShard(0);

  }

  private Object open_shard_lock = new Object();

  /**
   * open a shard for this node to interact with it
   * assuming we don't already have it open and it is
   * part of out interest set
   */
  public void openShard(int shard_id)
    throws Exception
  {
    if (shard_comps.containsKey(shard_id)) return;
    if (!shard_interest_set.contains(shard_id)) return;

    synchronized(open_shard_lock)
    {
      if (shard_comps.containsKey(shard_id)) return;

      ShardComponents shard_c = new ShardComponents(this, shard_id);

      TreeMap<Integer, ShardComponents> m = new TreeMap<>();
      m.putAll(shard_comps);
      m.put(shard_id, shard_c);

      shard_comps = ImmutableMap.copyOf(m);
    }
  }

  public void tryOpenShard(int shard_id)
  {
    try
    {
      openShard(shard_id);
    }
    catch(Exception e)
    {
      logger.warning(e.toString());
    }

  }

  private void startWidgets()
  {
    logger.fine("Widget start");
    peerage.start();
    new TimeWatcher().start();
    tx_broadcaster.start();

    //new Ender(this).start();
  }

  private void startServices()
    throws Exception
  {
    user_service = new SnowUserService(this);
    peer_service = new SnowPeerService(this);
    LinkedList<Integer> ports = new LinkedList<>();
    LinkedList<Integer> tls_ports = new LinkedList<>();
    LinkedList<Integer> all_ports = new LinkedList<>();

    if (config.isSet("service_port"))
    {
      for(String port_str : config.getList("service_port"))
      {
        int port = Integer.parseInt(port_str);
        Server s = ServerBuilder
          .forPort(port)
          .addService(user_service)
          .addService(peer_service)
          .maxInboundMessageSize(params.getGrpcMaxMessageSize())
          .build();
        s.start();
        ports.add(s.getPort());
      }
    }

    if (config.isSet("tls_service_port"))
    {
      config.require("tls_key_path");
      WalletDatabase wallet_db = loadWalletFromConfig("tls_key_path");

      node_tls_address = AddressUtil.getHashForSpec(wallet_db.getAddresses(0));
      logger.info("My TLS address: " + AddressUtil.getAddressString(Globals.NODE_ADDRESS_STRING, node_tls_address));

      SslContext ssl_ctx = CertGen.getServerSSLContext(wallet_db);
      for(String port_str : config.getList("tls_service_port"))
      {
        int port = Integer.parseInt(port_str);
        Server s = NettyServerBuilder
          .forPort(port)
          .addService(user_service)
          .addService(peer_service)
          .maxInboundMessageSize(params.getGrpcMaxMessageSize())
          .sslContext(ssl_ctx)
          .build();
        s.start();
        tls_ports.add(s.getPort());
      }
    }

    service_ports = ImmutableList.copyOf(ports);
    tls_service_ports = ImmutableList.copyOf(tls_ports);

    all_ports.addAll(service_ports);
    all_ports.addAll(tls_service_ports);

    logger.info("Ports: " + service_ports + " " + tls_service_ports);

    NetTools.tryUPNP(all_ports);

    user_service.start();
    db_maint_thread.start();
  }


  /**
   * Loads or creates a single key wallet based on the config param name pointing
   * to a path.
   */
  private WalletDatabase loadWalletFromConfig(String param_name)
    throws Exception
  {
    if (!config.isSet(param_name)) return null;

    config.require(param_name);

    TreeMap<String, String> wallet_config_map = new TreeMap<>();
    wallet_config_map.put("wallet_path", config.get(param_name));
    wallet_config_map.put("key_count", "1");
    wallet_config_map.put("key_mode", WalletUtil.MODE_STANDARD);
    ConfigMem config_wallet = new ConfigMem(wallet_config_map);
    File wallet_path = new File(config_wallet.get("wallet_path"));

    WalletDatabase wallet_db = WalletUtil.loadWallet(wallet_path, true, params);
    if (wallet_db == null)
    {
      logger.log(Level.WARNING, String.format("Directory %s does not contain keys, creating new keys", wallet_path.getPath()));
      wallet_db = WalletUtil.makeNewDatabase(config_wallet, params);
      WalletUtil.saveWallet(wallet_db, wallet_path);

      AddressSpecHash spec = AddressUtil.getHashForSpec(wallet_db.getAddresses(0));
      String addr = AddressUtil.getAddressString("node", spec);

      File dir = new File(config.get(param_name));
      PrintStream out = new PrintStream(new FileOutputStream( new File(dir, "address.txt"), false));
      out.println(addr);
      out.close();

    }

    return wallet_db;

  }


  private void loadDB()
    throws Exception
  {
    String db_type = config.get("db_type");

    if(db_type.equals("rocksdb"))
    {
      if (!SystemUtil.isJvm64Bit())
      {
        logger.log(Level.SEVERE,"Java Virtual Machine is 32-bit.  rocksdb does not work with 32-bit jvm.");
        logger.log(Level.SEVERE,"Upgrade to 64-bit JVM or set db_type=lobstack");

        throw new RuntimeException("Needs 64-bit JVM for rocksdb");

      }
      db = new DB(config, new JRocksDB(config));
    }
    else if (db_type.equals("lobstack"))
    {
      db = new DB(config, new LobstackDB(config));
    }
    else if (db_type.equals("atomic_file"))
    {
      db = new DB(config, new AtomicFileDB(config));
    }
    else
    {
      logger.log(Level.SEVERE, String.format("Unknown db_type: %s", db_type));
      throw new RuntimeException("Unable to load DB");
    }

    db.open();

  }

  public boolean areWeSynced()
  {
    // Regtest network doesn't have this check for single instance networks
    if (params.allowSingleHost()) return true;

    int height = 0;
    int seen_height = 0;
    if (peerage.getConnectedPeerCount() == 0)
    {
      return false;
    }

    for(int shard : getActiveShards())
    {
      if (getBlockIngestor(shard).getHead() != null)
      {
        height = Math.max( 
          height,
          getBlockIngestor(shard).getHead().getHeader().getBlockHeight());
      }
    }
    
    if (peerage.getHighestSeenHeader() != null)
    {
      seen_height = getPeerage().getHighestSeenHeader().getBlockHeight();
    }

    int diff = seen_height - height;
    if (diff < 10) return true; //whatever

    return false;


  }

  public Config getConfig(){return config;}
  public DB getDB(){return db;}
  public NetworkParams getParams(){return params;}

  public ForgeInfo getForgeInfo(){return forge_info;}
  public BlockIngestor getBlockIngestor(){return getBlockIngestor(0);}
  public ShardBlockForge getBlockForge(){return shard_blockforge;}
  public MetaMemPool getMemPool(){return meta_mem_pool;}
  public TxBroadcaster getTxBroadcaster(){return tx_broadcaster;}

  public ShardUtxoImport getShardUtxoImport(){return shard_utxo_import;}

  public Set<Integer> getActiveShards(){return shard_comps.keySet(); }
  public Set<Integer> getInterestShards(){return shard_interest_set; }
  public Set<Integer> getConfigShards(){return shard_config_set;}

  
  private Object current_building_shards_lock = new Object();
  private ImmutableSet<Integer> current_building_shards = null;
  private long current_building_shards_time = 0;

  public Set<Integer> getCurrentBuildingShards()
  {
    synchronized(current_building_shards_lock)
    {
      if ((current_building_shards == null) || (System.currentTimeMillis() > current_building_shards_time + 2000L))
      {
        current_building_shards_time = System.currentTimeMillis();
        current_building_shards = ImmutableSet.copyOf(calcCurrentBuildingShards());
      }
      return current_building_shards;
    }
  }


  private Set<Integer> calcCurrentBuildingShards()
  {
    logger.fine("Recalculating current building shards");
    TreeSet<Integer> res = new TreeSet<>();

    for(int s : getActiveShards())
    {
      if (getBlockIngestor(s).getHead() != null)
      {
        BlockHeader s_head = getBlockIngestor(s).getHead().getHeader();
        int child = 0;
        for(int c : ShardUtil.getShardChildIds(s))
        {
          int active = 0;
          if (getForgeInfo().getNetworkActiveShards().containsKey(c)) active=1;
          if (getForgeInfo().getShardHead(c) != null)
          {
            active=1;

            // The case of if we split off the child shards but that
            // split got reorged out
            BlockHeader c_head = getForgeInfo().getShardHead(c);
            if (c_head.getBlockHeight() <= s_head.getBlockHeight())
            {
              active=0;
            }
          }


          if (active>0)
          {
            child++;
          }
        }
        if (child < 2) res.add(s);
      }
    }

    return res;
  }

  public BlockIngestor getBlockIngestor(int shard_id)
  {
    ShardComponents sc = shard_comps.get(shard_id);
    if (sc == null) return null;

    return sc.ingestor;
  }

  public BlockForge getBlockForge(int shard_id)
  {
    return shard_comps.get(shard_id).forge;
  }

  public MemPool getMemPool(int shard_id)
  {
    return shard_comps.get(shard_id).mem_pool;
  }

  public HashedTrie getUtxoHashedTrie(){return db.getUtxoHashedTrie();}
  public Peerage getPeerage(){return peerage;}
  public SnowUserService getUserService() {return user_service;}

  public ImmutableList<Integer> getServicePorts() {return service_ports;}
  public ImmutableList<Integer> getTlsServicePorts() {return tls_service_ports;}
  public AddressSpecHash getTlsAddress(){return node_tls_address;}
  public WalletDatabase getTrustnetWalletDb(){ return trustnet_wallet_db; }
  public AddressSpecHash getTrustnetAddress()
  {
    if (trustnet_wallet_db != null)
    {
      return AddressUtil.getHashForSpec(trustnet_wallet_db.getAddresses(0));
    }
    return null;
  }


  public class ShardComponents
  {
    protected BlockIngestor ingestor;
    protected BlockForge forge;
    protected MemPool mem_pool;

    public ShardComponents(SnowBlossomNode node, int shard_id)
      throws Exception
    {
      ingestor = new BlockIngestor(node, shard_id);
      forge = new BlockForge(node, shard_id);
      boolean accept_p2p = !config.getBoolean("mempool_reject_p2p_tx");

      mem_pool = new MemPool(db.getUtxoHashedTrie(), ingestor, Globals.LOW_FEE_SIZE_IN_BLOCK, accept_p2p);
      mem_pool.setPeerage(peerage);
    }

  }

}
