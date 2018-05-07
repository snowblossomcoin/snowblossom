package snowblossom;

import snowblossom.proto.PeerServiceGrpc;
import snowblossom.proto.SubscribeBlockTemplateRequest;
import snowblossom.proto.Block;
import snowblossom.proto.BlockHeader;
import snowblossom.proto.SubmitReply;
import snowblossom.proto.PeerMessage;
import io.grpc.stub.StreamObserver;

import java.util.Random;
import java.util.logging.Logger;
import com.google.protobuf.ByteString;
import java.nio.ByteBuffer;

import java.util.LinkedList;
import java.util.TreeMap;


public class SnowPeerService extends PeerServiceGrpc.PeerServiceImplBase
{
  private static final Logger logger = Logger.getLogger("SnowPeerService");

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

