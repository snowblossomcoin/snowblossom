package snowblossom;

import io.grpc.stub.StreamObserver;
import snowblossom.proto.PeerMessage;
import io.grpc.ManagedChannel;

import java.util.logging.Logger;
import java.util.logging.Level;


/**
 * This class exists to present a single view of a peer regardless
 * of if we are the client or server.  We don't really care.
 * Messages to the other side go out on the 'sink'.
 * Messages come in on the onNext() method.
 */
public class PeerLink implements StreamObserver<PeerMessage>
{
  private static final Logger logger = Logger.getLogger("PeerLink");

  private StreamObserver<PeerMessage> sink;
  private ManagedChannel channel;
  private volatile boolean closed;

  private boolean server_side;

  public PeerLink(StreamObserver<PeerMessage> sink)
  {
    this.sink = sink;
    server_side=true;
  }

  public PeerLink()
  {
    server_side=false;
  }
  public void setSink(StreamObserver<PeerMessage> sink)
  {
    this.sink=sink;
  }
  public void setChannel(ManagedChannel channel)
  {
    this.channel = channel;
  }

  @Override
  public void onCompleted()
  {
    close();
  }

  @Override
  public void onError(Throwable t)
  {
    logger.log(Level.INFO,"link error", t);
    close();
  }

  @Override
  public void onNext(PeerMessage msg)
  {

  }

  public void close()
  {
    closed=true;
    sink.onCompleted();

    if (channel != null)
    {
      channel.shutdown();
    }
  }

  public boolean isOpen()
  {
    return !closed;
  }
  
  public void writeMessage(PeerMessage msg)
  {
    synchronized(sink)
    {
      sink.onNext(msg);
    }
  }

}
