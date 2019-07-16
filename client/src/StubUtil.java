package snowblossom.client;
import com.google.common.collect.ImmutableMap;
import duckutil.Config;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.Globals;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.tls.SnowTrustManagerFactorySpi;
import snowblossom.proto.NodeStatus;
import snowblossom.proto.NullRequest;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import snowblossom.proto.UserServiceGrpc;

public class StubUtil
{
  private static final Logger logger = Logger.getLogger("snowblossom.client");

  public static ManagedChannel openChannel(Config config, NetworkParams params)
    throws Exception
  {
    if (config.isSet("node_seed"))
    {

      if (config.isSet("node_uri"))
      {
        throw new Exception("node_uri and node_seed are mutually exclusive. Pick one.");
      }
      if (config.isSet("node_host"))
      {
        throw new Exception("node_host and node_seed are mutually exclusive. Pick one.");
      }

      if (config.isSet("node_port"))
      {
        throw new Exception("node_port is only for node_host, not node_uri");
      }
 
      return findFastestChannel( params.getSeedUris(), params);
    }
		if (config.isSet("node_uri"))
    {
      if (config.isSet("node_host"))
      {
        throw new Exception("node_host and node_uri are mutually exclusive. Pick one.");
      }

      if (config.isSet("node_port"))
      {
        throw new Exception("node_port is only for node_host, not node_uri");
      }
      List<String> uri_list = config.getList("node_uri");
      if (uri_list.size() > 1)
      {
        return findFastestChannel(uri_list, params);
      }
      return openChannel(config.get("node_uri"), params);
    }
    if (config.isSet("node_host"))
    {
      String uri = "grpc://" + config.get("node_host");
      if (config.isSet("node_port"))
      {
        uri += ":" + config.get("node_port");
      }
      return openChannel(uri, params);
    }

    return findFastestChannel( params.getSeedUris(), params);
  }

  public static ManagedChannel openChannel(String uri, NetworkParams params)
    throws Exception
  {
    URI u = new URI(uri);

    String host = u.getHost();
    int port = u.getPort();
    String scheme = u.getScheme();
    if (scheme == null) scheme="grpc";

    if (port == -1)
    {
      if (scheme.equals("grpc"))
      {
        port = params.getDefaultPort();
      }
      else if (scheme.equals("grpc+tls"))
      {
        port = params.getDefaultTlsPort();
      }
      else
      {
        throw new Exception("Unknown scheme: " + scheme);
      }
    }

    if (scheme.equals("grpc"))
    {
      if (port == -1) port = params.getDefaultPort();
      return ManagedChannelBuilder
        .forAddress(host, port)
        .usePlaintext(true)
        .build();
    }
    else if (scheme.equals("grpc+tls"))
    {
      if (port == -1) port = params.getDefaultTlsPort();
      AddressSpecHash expected_key = null;

      String query=u.getQuery();
      Properties query_props = new Properties();
      if (query!=null)
      {
        query_props.load(new ByteArrayInputStream( query.replace('&','\n').getBytes() ));

        if (query_props.getProperty("key") != null)
        {
          expected_key = new AddressSpecHash(query_props.getProperty("key"), Globals.NODE_ADDRESS_STRING);
        }
      }

      SslContext ssl_ctx = GrpcSslContexts.forClient()
        .trustManager(SnowTrustManagerFactorySpi.getFactory(expected_key, params))
        .build();
      return NettyChannelBuilder
        .forAddress(host, port)
        .useTransportSecurity()
        .sslContext(ssl_ctx)
        .build();
    }
    else
    {
      throw new Exception("Unknown scheme: " + scheme);
    }
  }

  public static UserServiceBlockingStub getBlockingStub(ManagedChannel channel)
  {
    return UserServiceGrpc.newBlockingStub(channel);
  }

  public static UserServiceStub getAsyncStub(ManagedChannel channel)
  {
    return UserServiceGrpc.newStub(channel);
  }

  public static ManagedChannel findFastestChannel(Collection<String> uris, NetworkParams params)
    throws Exception
  {
    return findFastestChannelMonitor(uris, params).getManagedChannel();
  }

  public static ChannelMonitor findFastestChannelMonitor(Collection<String> uris, NetworkParams params)
    throws StubException
  {
    ChannelMonitor mon = new ChannelMonitor();

    int option_count =0;
    for(String uri : uris)
    {
      try
      {
        logger.log(Level.FINE, "Starting uri check: " + uri);
        ManagedChannel mc = openChannel(uri, params);
        UserServiceStub stub = getAsyncStub(mc);

        stub.getNodeStatus(NullRequest.newBuilder().build(), new StatusObserver(mon, mc, uri));
        option_count++;
      }
      catch(Exception e)
      {
        logger.log(Level.FINE, "Error on uri: " + uri, e);
        mon.recordError(uri, e.toString());
      }
    }
    if (option_count == 0)
    {
      throw new StubException("No valid URIs to select from");
    }

    try
    {

      if (mon.waitFor(uris.size()))
      {
        String uri = mon.getUri();
        logger.log(Level.INFO, "Selected node: " + uri);
      }
      else
      {
        throw new StubException("No nodes returned within timeout", mon.getErrorMap());
      } 
    }
    catch(InterruptedException e)
    {
      throw new StubException(e.toString());
    }

    return mon;

  }

  public static class ChannelMonitor
  {
    private ManagedChannel mc;
    private String uri;
    private NodeStatus ns;
    private TreeMap<String, String> error_map = new TreeMap<>();
    public static final int TIMEOUT_MS=60000;

    public synchronized void setMonitor(NodeStatus ns, ManagedChannel mc, String uri)
    {
      if (this.ns == null)
      {
        this.ns = ns;
        this.mc = mc;
        this.uri = uri;
      }
      this.notifyAll();
    }

    public synchronized boolean waitFor(int expected_count)
      throws InterruptedException
    {
      long t_end = System.currentTimeMillis() + TIMEOUT_MS;

      while((error_map.size() < expected_count) && (System.currentTimeMillis() < t_end) && (uri == null))
      {
        this.wait(500);
      }
      return (uri != null);

    }

    public synchronized NodeStatus getNodeStatus()
    {
      return ns;
    }
    public synchronized String getUri()
    {
      return uri;
    }
    public synchronized ManagedChannel getManagedChannel()
    {
      return mc;
    }
    public synchronized void recordError(String uri, String error)
    {
      error_map.put(uri, error);
    }

    public synchronized Map<String,String> getErrorMap()
    {
      return ImmutableMap.copyOf(error_map);
    }
  }

  public static class StatusObserver implements StreamObserver<NodeStatus>
  {
    private ManagedChannel mc;
    private String uri;
    private ChannelMonitor mon;

    public StatusObserver(ChannelMonitor mon, ManagedChannel mc, String uri)
    {
      this.mc = mc;
      this.uri = uri;
      this.mon = mon;

    }
    public void onNext(NodeStatus ns)
    {
      mon.setMonitor(ns, mc, uri);
    }
    public void onError(Throwable t)
    {
      logger.log(Level.FINE, "Error on uri: " + uri, t);
      mon.recordError(uri, t.toString());

    }
    public void onCompleted()
    {

    }

  }

}
