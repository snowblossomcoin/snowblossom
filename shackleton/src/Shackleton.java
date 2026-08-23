package snowblossom.shackleton;

import duckutil.Config;
import duckutil.ConfigFile;
import duckutil.PeriodicThread;
import io.grpc.ManagedChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.client.GetUTXOUtil;
import snowblossom.client.StubHolder;
import snowblossom.client.StubUtil;
import snowblossom.lib.Globals;
import snowblossom.lib.LogSetup;
import snowblossom.lib.NetworkParams;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import snowblossom.proto.UserServiceGrpc;

/** Yes a penguin taught me french back in antacrtica */
public class Shackleton
{
  private static final Logger logger = Logger.getLogger("snowblossom.shackleton");

  public static void main(String args[])
    throws Exception
  {
    Globals.addCryptoProvider();
    if (args.length != 1)
    {
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: Shackleton <config_file>");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0], "snowblossom_");

    LogSetup.setup(config);

    new Shackleton(config);
  }

  private final Config config;
  private final long start_time;
  private WebServer web_server;
  private UserServiceStub asyncStub;
  private UserServiceBlockingStub blockingStub;
  private GetUTXOUtil get_utxo_util;
  private NetworkParams params;
  private HealthStats health_stats;
  private ChainCache cache;
  private ChainIndex index;
  private TxLocator tx_locator;
  private MiningStats mining_stats;
  private HistoryStats history_stats;
  private RichList rich_list;
  private java.io.File richlist_cache;

  public Shackleton(Config config)
    throws Exception
  {
    this.config = config;
    this.start_time = System.currentTimeMillis();

    params = NetworkParams.loadFromConfig(config);

    ManagedChannel channel = StubUtil.openChannel(config, params);

    asyncStub = UserServiceGrpc.newStub(channel);
    blockingStub = UserServiceGrpc.newBlockingStub(channel);
    get_utxo_util = new GetUTXOUtil(new StubHolder(channel), params);

    cache = new ChainCache(this);
    index = new ChainIndex(this, config.getIntWithDefault("index_depth", 300));
    tx_locator = new TxLocator(this);
    rich_list = new RichList(params, blockingStub, get_utxo_util);
    mining_stats = new MiningStats(this, config.getIntWithDefault("mining_look_back", 1000));
    history_stats = new HistoryStats(this, config.getIntWithDefault("history_days", 365));
    health_stats = new HealthStats(this);

    web_server = new WebServer(config, this);

    index.start();
    mining_stats.start();
    history_stats.start();
    richlist_cache = new java.io.File(config.getWithDefault("richlist_cache_file", "shackleton-richlist.cache"));
    rich_list.load(richlist_cache);
    new RichListUpdateThread(config.getIntWithDefault("richlist_interval_min", 45)).start();
  }

  public Config getConfig() { return config; }
  public long getStartTime() { return start_time; }
  public UserServiceBlockingStub getStub() { return blockingStub; }
  public UserServiceStub getAsyncStub() { return asyncStub; }
  public NetworkParams getParams() { return params; }
  public GetUTXOUtil getUtxoUtil() { return get_utxo_util; }
  public HealthStats getHealthStats() { return health_stats; }
  public ChainCache getCache() { return cache; }
  public ChainIndex getIndex() { return index; }
  public TxLocator getTxLocator() { return tx_locator; }
  public MiningStats getMiningStats() { return mining_stats; }
  public HistoryStats getHistoryStats() { return history_stats; }
  public RichList getRichList() { return rich_list; }

  public long getTotalValue() throws Exception { return rich_list.getTotalValue(); }

  public class RichListUpdateThread extends PeriodicThread
  {
    public RichListUpdateThread(int interval_min)
    {
      super(Math.max(5, interval_min) * 60L * 1000L);
      setName("RichListUpdateThread");
      setDaemon(true);
    }

    public void runPass() throws Exception
    {
      // let the chain index come up first so the first page loads are fast
      for (int i = 0; (i < 60) && !index.isReady(); i++) sleep(1000L);
      logger.info("Started rich list update");
      try
      {
        RichList.Result r = rich_list.compute();
        rich_list.save(r, richlist_cache);
      }
      catch (Throwable t)
      {
        logger.log(Level.WARNING, "Rich list update failed: " + t, t);
      }
      logger.info("Completed rich list update");
    }
  }
}
