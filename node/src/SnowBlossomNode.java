package snowblossom.node;

import com.google.common.collect.ImmutableList;
import duckutil.Config;
import duckutil.ConfigFile;
import duckutil.ConfigMem;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.netty.handler.ssl.SslContext;
import java.io.File;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.client.WalletUtil;
import snowblossom.lib.*;
import snowblossom.lib.db.DB;
import snowblossom.lib.db.lobstack.LobstackDB;
import snowblossom.lib.db.rocksdb.JRocksDB;
import snowblossom.lib.tls.CertGen;
import snowblossom.lib.trie.HashedTrie;
import snowblossom.lib.trie.TrieDBMap;
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
  private BlockIngestor ingestor;
  private BlockForge forge;
  private MemPool mem_pool;
  private HashedTrie utxo_hashed_trie;
  private Peerage peerage;
  private BlockHeightCache block_height_cache;

  private ImmutableList<Integer> service_ports;
  private ImmutableList<Integer> tls_service_ports;
  private AddressSpecHash node_tls_address;

  private volatile boolean terminate;

  public SnowBlossomNode(Config config)
    throws Exception
  {
    this.config = config;

    config.require("db_type");
    logger.info(String.format("Starting SnowBlossomNode version %s", Globals.VERSION));

    setupParams();
    loadDB();
    loadUtxoDB();
    loadWidgets();

    startServices();

    startWidgets();
  }

  public void stop()
  {
    terminate=true;

  }

  private void setupParams()
  {
    params = NetworkParams.loadFromConfig(config);
  }

  private void loadWidgets()
    throws Exception
  {
    ingestor = new BlockIngestor(this);
    forge = new BlockForge(this);
    mem_pool = new MemPool(utxo_hashed_trie, ingestor);

    peerage = new Peerage(this);
    mem_pool.setPeerage(peerage);

    block_height_cache = new BlockHeightCache(this);

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
      db = new DB(config, new JRocksDB(config));
    }
    else if (db_type.equals("lobstack"))
    {
      db = new DB(config, new LobstackDB(config));
    }
    else
    {
      logger.log(Level.SEVERE, String.format("Unknown db_type: %s", db_type));
      throw new RuntimeException("Unable to load DB");
    }

    db.open();

  }
  private void loadUtxoDB()
    throws Exception
  {
    //config.require("utxo_db_path");
    //String utxo_db_path = config.get("utxo_db_path");
    //File utxo_db_file = new File(utxo_db_path);
    //utxo_db_file.mkdirs();

    utxo_hashed_trie = new HashedTrie(new TrieDBMap(db.getUtxoNodeMap()), true, false);
  }

  public boolean areWeSynced()
  {
    // Regtest network doesn't have this check for single instance networks
    if (params.getNetworkName().equals("spoon")) return true;

    int height = 0;
    int seen_height = 0;
    if (peerage.getConnectedPeerCount() == 0)
    {
      return false;
    }
    if (getBlockIngestor().getHead() != null)
    {
      height = getBlockIngestor().getHead().getHeader().getBlockHeight();
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
  public BlockIngestor getBlockIngestor(){ return ingestor; }
  public BlockForge getBlockForge() {return forge;}
  public HashedTrie getUtxoHashedTrie(){return utxo_hashed_trie;}
  public MemPool getMemPool(){return mem_pool;}
  public Peerage getPeerage(){return peerage;}
  public SnowUserService getUserService() {return user_service;}
  public BlockHeightCache getBlockHeightCache() {return block_height_cache; }
  public ImmutableList<Integer> getServicePorts() {return service_ports;}
  public ImmutableList<Integer> getTlsServicePorts() {return tls_service_ports;}
  public AddressSpecHash getTlsAddress(){return node_tls_address;}

}
