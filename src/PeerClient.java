package snowblossom;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import snowblossom.proto.PeerServiceGrpc.PeerServiceStub;
import snowblossom.proto.PeerServiceGrpc;

import snowblossom.proto.PeerMessage;


public class PeerClient
{
  public PeerClient(SnowBlossomNode node, String host, int port)
  {
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
    PeerServiceStub asyncStub = PeerServiceGrpc.newStub(channel);

    PeerLink link = new PeerLink(node);
    StreamObserver<PeerMessage> sink = asyncStub.subscribePeering(link);
    link.setSink(sink);
    link.setChannel(channel);

    node.getPeerage().register(link);

  }


}
