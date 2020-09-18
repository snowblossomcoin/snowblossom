package snowblossom.node;

import io.grpc.stub.StreamObserver;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.proto.PeerMessage;
import snowblossom.proto.PeerServiceGrpc;

public class SnowPeerService extends PeerServiceGrpc.PeerServiceImplBase
{
  private static final Logger logger = Logger.getLogger("snowblossom.peering");

  private SnowBlossomNode node;

  public SnowPeerService(SnowBlossomNode node)
  {
    super();
    this.node = node;

  }


  @Override
  public StreamObserver<PeerMessage> subscribePeering(StreamObserver<PeerMessage> sink)
  {
    PeerLink link = new PeerLink(node, sink);
    node.getPeerage().register(link);
    return link;
  }

}
