package snowblossom.shackleton;

import snowblossom.Config;
import snowblossom.ConfigFile;
import snowblossom.Globals;
import snowblossom.LogSetup;

import java.util.logging.Logger;
import java.util.logging.Level;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc;

import snowblossom.NetworkParams;


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
  private NetworkParams params;

  public Shackleton(Config config)
    throws Exception
  {
    this.config = config;

    web_server = new WebServer(config, this);
    config.require("node_host");

    params = NetworkParams.loadFromConfig(config);

    String host = config.get("node_host");
    int port = config.getIntWithDefault("node_port", params.getDefaultPort());
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();

    asyncStub = UserServiceGrpc.newStub(channel);
    blockingStub = UserServiceGrpc.newBlockingStub(channel);
    
    web_server.start();

  }


  public UserServiceBlockingStub getStub()
  {
    return blockingStub;
  }


}