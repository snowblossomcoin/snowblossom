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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
  private MetaBlockForge meta_blockforge;

  private ImmutableList<Integer> service_ports;
  private ImmutableList<Integer> tls_service_ports;
  private AddressSpecHash node_tls_address;
  private ImmutableList<StatusInterface> status_list;
  private ImmutableSet<Integer> shard_interest_set;

  private Map<Integer, ShardComponents> shard_comps = ImmutableMap.of();

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

  private void setupParams()
  {
    params = NetworkParams.loadFromConfig(config);
    
    TreeSet<Integer> cover_set = new TreeSet<>();

    if (config.isSet("shards"))
    {
      for(String shard_str : config.getList("shards"))
      {
        int shard_id = Integer.parseInt(shard_str);

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
    }
    logger.info("Shard interest set: " + cover_set);

    shard_interest_set = ImmutableSet.copyOf(cover_set);

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

    peerage = new Peerage(this);
    meta_blockforge = new MetaBlockForge(this);

  }

  /**
   * open shards where we know about a head
   */
  private void openShards()
    throws Exception
  {
    for(int shard_id : shard_interest_set)
    {
      if ( new BlockIngestor(this, shard_id).getHead() != null)
      {
        openShard(shard_id);
      }

    }
    // If we have nothing, open shard zero regardless
    if(shard_comps.size() == 0) openShard(0);

  }

  private Object open_shard_lock = new Object();

  public void openShard(int shard_id)
    throws Exception
  {
    if (shard_comps.containsKey(shard_id)) return;

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

  private void startWidgets()
  {
    logger.fine("Widget start");
    peerage.start();
    new TimeWatcher().start();
  }

  private void startServices()
    throws Exception
  {
    user_service = new SnowUserService(this);
    peer_service = new SnowPeerService(this);
    LinkedList<Integer> ports = new LinkedList<>();
    LinkedList<Integer> tls_ports = new LinkedList<>();

    if (config.isSet("service_port"))
    {
      for(String port_str : config.getList("service_port"))
      {
        int port = Integer.parseInt(port_str);
        ports.add(port);
        Server s = ServerBuilder
          .forPort(port)
          .addService(user_service)
          .addService(peer_service)
          .maxInboundMessageSize(params.getGrpcMaxMessageSize())
          .build();
        s.start();
      }
    }

    if (config.isSet("tls_service_port"))
    {
      config.require("tls_key_path");
      TreeMap<String, String> wallet_config_map = new TreeMap<>();
      wallet_config_map.put("wallet_path", config.get("tls_key_path"));
      wallet_config_map.put("key_count", "1");
      wallet_config_map.put("key_mode", WalletUtil.MODE_STANDARD);
      ConfigMem config_wallet = new ConfigMem(wallet_config_map);
      File wallet_path = new File(config_wallet.get("wallet_path"));

      WalletDatabase wallet_db = WalletUtil.loadWallet(wallet_path, true, params);
      if (wallet_db == null)
      {
        logger.log(Level.WARNING, String.format("Directory %s does not contain tls keys, creating new keys", wallet_path.getPath()));
        wallet_db = WalletUtil.makeNewDatabase(config_wallet, params);
        WalletUtil.saveWallet(wallet_db, wallet_path);
      }
      node_tls_address = AddressUtil.getHashForSpec(wallet_db.getAddresses(0));
      logger.info("My TLS address: " + AddressUtil.getAddressString(Globals.NODE_ADDRESS_STRING, node_tls_address));

      SslContext ssl_ctx = CertGen.getServerSSLContext(wallet_db);
      for(String port_str : config.getList("tls_service_port"))
      {
        int port = Integer.parseInt(port_str);
        tls_ports.add(port);
        Server s = NettyServerBuilder
          .forPort(port)
          .addService(user_service)
          .addService(peer_service)
          .maxInboundMessageSize(params.getGrpcMaxMessageSize())
          .sslContext(ssl_ctx)
          .build();
        s.start();
      }
    }

    service_ports = ImmutableList.copyOf(ports);
    tls_service_ports = ImmutableList.copyOf(tls_ports);
    logger.info("Ports: " + service_ports + " " + tls_service_ports);

    user_service.start();
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

    // TODO - be shard aware
    if (getBlockIngestor(0).getHead() != null)
    {
      height = getBlockIngestor(0).getHead().getHeader().getBlockHeight();
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

  public BlockIngestor getBlockIngestor(){return getBlockIngestor(0);}
  public MetaBlockForge getBlockForge(){return meta_blockforge;}
  public MemPool getMemPool(){return getMemPool(0);}

  public Set<Integer> getActiveShards(){return shard_comps.keySet(); }

  public BlockIngestor getBlockIngestor(int shard_id)
  {
    return shard_comps.get(shard_id).ingestor; 
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
      mem_pool = new MemPool(db.getUtxoHashedTrie(), ingestor);
      mem_pool.setPeerage(peerage);
    }

  }

}
