package snowblossom.node;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;
import snowblossom.lib.*;
import snowblossom.lib.tls.SnowTrustManagerFactorySpi;
import snowblossom.proto.PeerInfo;
import snowblossom.proto.PeerMessage;
import snowblossom.proto.PeerServiceGrpc.PeerServiceStub;
import snowblossom.proto.PeerServiceGrpc;

public class PeerClient
{
  public PeerClient(SnowBlossomNode node, PeerInfo info)
    throws Exception
  {
    ManagedChannel channel;
    if (info.getConnectionType().equals(PeerInfo.ConnectionType.GRPC_TLS))
    {
      AddressSpecHash node_address = new AddressSpecHash(info.getNodeSnowAddress());
      SslContext ssl_ctx = GrpcSslContexts.forClient()
        .trustManager(SnowTrustManagerFactorySpi.getFactory(node_address, node.getParams()))
        .build();

      channel = NettyChannelBuilder
        .forAddress(info.getHost(), info.getPort())
        .useTransportSecurity()
        .sslContext(ssl_ctx)
        .build();
    }
    else if (info.getConnectionType().equals(PeerInfo.ConnectionType.GRPC_TCP))
    {
      channel = ManagedChannelBuilder
        .forAddress(info.getHost(), info.getPort())
        .usePlaintext(true)
        .build();
    }
    else
    {
      throw new Exception("Unknown connection type: " + info.getConnectionType());
    }
    PeerLink link = null;

    try
    {
      PeerServiceStub asyncStub = PeerServiceGrpc.newStub(channel);

      link = new PeerLink(node, PeerUtil.getString(info), info);
      StreamObserver<PeerMessage> sink = asyncStub.subscribePeering(link);
      link.setSink(sink);
      link.setChannel(channel);
    }
    catch(Throwable t)
    {
      link.close();
    }
    node.getPeerage().register(link);
  }
}
