package snowblossom.node;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import snowblossom.proto.PeerInfo;
import snowblossom.proto.PeerMessage;
import snowblossom.proto.PeerServiceGrpc;
import snowblossom.proto.PeerServiceGrpc.PeerServiceStub;
import snowblossom.lib.*;

import io.netty.handler.ssl.SslContext;
import io.grpc.netty.GrpcSslContexts;
import snowblossom.lib.tls.SnowTrustManagerFactorySpi;
import io.grpc.netty.NettyChannelBuilder;



public class PeerClient
{
  public PeerClient(SnowBlossomNode node, PeerInfo info)
    throws Exception
  {
    ManagedChannel channel;
    if (info.getTls())
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
    else
    {

      channel = ManagedChannelBuilder
        .forAddress(info.getHost(), info.getPort())
        .usePlaintext(true)
        .build();

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
