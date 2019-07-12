package snowblossom.client;

import io.grpc.ManagedChannel;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;

/**
 * Holds a reference to a ManagedChannel and stubs.
 * Allows them to be replaced without the user of them caring.
 */
public class StubHolder
{
  private volatile ManagedChannel channel;
  private volatile UserServiceStub stub;
  private volatile UserServiceBlockingStub blocking_stub;

  public StubHolder()
  {

  }
  
  public StubHolder(ManagedChannel channel)
  {
    update(channel);
  }

  public void update(ManagedChannel channel)
  {
    this.channel = channel;
    this.stub = StubUtil.getAsyncStub(channel);
    this.blocking_stub = StubUtil.getBlockingStub(channel);
  }

  public UserServiceBlockingStub getBlockingStub(){return blocking_stub;}
  public UserServiceStub getAsyncStub(){return stub;}

}
