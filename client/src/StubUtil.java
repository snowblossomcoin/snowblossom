package snowblossom.client;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.net.URI;
import java.io.ByteArrayInputStream;
import java.util.Properties;

import io.netty.handler.ssl.SslContext;
import io.grpc.netty.GrpcSslContexts;
import snowblossom.lib.tls.SnowTrustManagerFactorySpi;
import io.grpc.netty.NettyChannelBuilder;


import duckutil.Config;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.Globals;
import snowblossom.proto.UserServiceGrpc;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;

public class StubUtil
{
  public static ManagedChannel openChannel(Config config, NetworkParams params)
    throws Exception
  {
		if (config.isSet("node_uri"))
    {
      if (config.isSet("node_host"))
      {
        throw new Exception("node_host and node_uri are mutually exclusive. Pick one.");
      }

      if (config.isSet("node_port"))
      {
        throw new Exception("node_port is only for node_host, not node_url");
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

    throw new Exception("Must have either 'node_uri' or 'node_host'");
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

}
