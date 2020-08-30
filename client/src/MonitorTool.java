package snowblossom.client;

import io.grpc.stub.StreamObserver;
import snowblossom.proto.AddressUpdate;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.NetworkParams;
import snowblossom.proto.RequestAddress;
import snowblossom.proto.RequestTransaction;
import snowblossom.proto.HistoryList;
import snowblossom.proto.HistoryEntry;
import snowblossom.proto.TransactionHashList;
import snowblossom.proto.Transaction;
import snowblossom.proto.TransactionInner;
import snowblossom.proto.TransactionInput;
import snowblossom.proto.TransactionOutput;
import snowblossom.lib.TransactionUtil;

import com.google.protobuf.ByteString;

import java.util.HashSet;

/**
 * For any addresses added via addAddress this will cause the MonitorInterface
 * to be called for any transactions in or out of that address.
 *
 * Includes all historical transactions.
 */
public class MonitorTool implements StreamObserver<AddressUpdate>
{
  private NetworkParams params;
  private StubHolder stub_holder;
  private MonitorInterface monitor_interface;
  private HashSet<AddressSpecHash> addresses = new HashSet<>();

  // ByteString of address spec hash and then tx hash.
  private HashSet<ByteString> processed_tx = new HashSet<>();

  public MonitorTool(NetworkParams params, StubHolder stub_holder, MonitorInterface monitor_interface)
  {
    this.params = params;
    this.stub_holder = stub_holder;
    this.monitor_interface = monitor_interface;
  }


  public void addAddress(AddressSpecHash hash)
  {
    synchronized(addresses)
    {
      if (addresses.contains(hash)) return;
      addresses.add(hash);
    }

    stub_holder.getAsyncStub().subscribeAddressUpdates(
      RequestAddress.newBuilder().setAddressSpecHash(hash.getBytes()).build(),
      this);
  }

  private void triggerAddress(AddressSpecHash hash)
  {
    RequestAddress ra = RequestAddress.newBuilder().setAddressSpecHash(hash.getBytes()).build();

    HistoryList hl = stub_holder.getBlockingStub().getAddressHistory(ra);
    TransactionHashList ml = stub_holder.getBlockingStub().getMempoolTransactionList(ra);

    for(HistoryEntry he : hl.getEntriesList())
    {
      ByteString tx_hash = he.getTxHash();
      sendNotices(hash, tx_hash);

    }
    for(ByteString tx_hash : ml.getTxHashesList())
    {
      sendNotices(hash, tx_hash);

    }
  }

  private void sendNotices(AddressSpecHash hash, ByteString tx_hash)
  {
    ByteString key = hash.getBytes().concat(tx_hash);
    synchronized(processed_tx)
    {
      if (processed_tx.contains(key)) return;
    }

    Transaction tx = stub_holder.getBlockingStub().getTransaction( RequestTransaction.newBuilder().setTxHash( tx_hash ).build() );
    TransactionInner inner = TransactionUtil.getInner(tx);

    int idx = 0;
    for (TransactionInput in : inner.getInputsList())
    {
      if (hash.getBytes().equals(in.getSpecHash()))
      {
        monitor_interface.onOutbound(tx, idx);
      }
      idx++;
    }

    idx=0;
    for (TransactionOutput out : inner.getOutputsList())
    {
      if (hash.getBytes().equals(out.getRecipientSpecHash()))
      {
        monitor_interface.onInbound(tx, idx);
      }
      idx++;
    }

    synchronized(processed_tx)
    {
      processed_tx.add(key);
    }


  }

  public void onNext(AddressUpdate update)
  {
    AddressSpecHash hash = new AddressSpecHash(update.getAddress());

    triggerAddress(hash);
  }

  public void onCompleted()
  {

  }

  public void onError(Throwable t)
  {
    t.printStackTrace();

  }

}
