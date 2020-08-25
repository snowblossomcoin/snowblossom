package snowblossom.client;

import io.grpc.stub.StreamObserver;
import snowblossom.proto.AddressUpdate;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.NetworkParams;

public class MonitorUtil implements StreamObserver<AddressUpdate>
{
  private NetworkParams params;

  public MonitorUtil(NetworkParams params)
  {
    this.params = params;
  }

  public void onNext(AddressUpdate update)
  {
    AddressSpecHash hash = new AddressSpecHash(update.getAddress());

    System.out.println( hash.toAddressString(params));
    System.out.println(update);
  }

  public void onCompleted()
  {

  }

  public void onError(Throwable t)
  {
    t.printStackTrace();

  }

}
