package snowblossom.shackleton;

import duckutil.Config;
import duckutil.ConfigFile;
import duckutil.PeriodicThread;
import io.grpc.ManagedChannel;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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

    ConfigFile config = new ConfigFile(args[0]);

    LogSetup.setup(config);

    new Shackleton(config);

  }

  private Config config;
  private WebServer web_server;
  private UserServiceStub asyncStub;
  private UserServiceBlockingStub blockingStub;
  private GetUTXOUtil get_utxo_util;
  private NetworkParams params;

  private VoteTracker vote_tracker;
  private RichList rich_list;

  private volatile String rich_list_report = "not computed yet";

  public Shackleton(Config config)
    throws Exception
  {
    this.config = config;


    params = NetworkParams.loadFromConfig(config);

    ManagedChannel channel = StubUtil.openChannel(config, params);

    asyncStub = UserServiceGrpc.newStub(channel);
    blockingStub = UserServiceGrpc.newBlockingStub(channel);
    get_utxo_util = new GetUTXOUtil(new StubHolder(channel), params);

    rich_list = new RichList(params, blockingStub, get_utxo_util);
    
    vote_tracker=new VoteTracker(this);
    vote_tracker.start();
    
    web_server = new WebServer(config, this);

    new RichListUpdateThread().start();

  }

  public UserServiceBlockingStub getStub()
  {
    return blockingStub;
  }

  public NetworkParams getParams()
  {
    return params;
  }
  public VoteTracker getVoteTracker(){return vote_tracker;}
  public GetUTXOUtil getUtxoUtil(){ return get_utxo_util;}

  public String getRichListReport(){return rich_list_report;}

  public long getTotalValue() throws Exception {return rich_list.getTotalValue();}


  public class RichListUpdateThread extends PeriodicThread
  {
    public RichListUpdateThread()
    {
      super(45L * 60L * 1000L);
      setName("RichListUpdateThread");
      setDaemon(true);
    }

    public void runPass() throws Exception
    {
      logger.info("Started rich list update");
      sleep(5000L);

      ByteArrayOutputStream b_out = new ByteArrayOutputStream();
      PrintStream p_out = new PrintStream(b_out);

      rich_list.print(p_out);
      p_out.close();

      rich_list_report = new String(b_out.toByteArray());
      
      logger.info("Completed rich list update");

    }

  }
}
