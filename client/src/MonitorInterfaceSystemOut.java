package snowblossom.client;

import snowblossom.proto.Transaction;
import snowblossom.lib.ChainHash;

public class MonitorInterfaceSystemOut implements MonitorInterface
{
  public void onInbound(Transaction tx, int tx_in_idx)
  {
    ChainHash hash = new ChainHash(tx.getTxHash());
    System.out.println("Inbound tx: " + hash + " input: " + tx_in_idx);
  }

  public void onOutbound(Transaction tx, int tx_out_idx)
  {
    ChainHash hash = new ChainHash(tx.getTxHash());
    System.out.println("Outbound tx: " + hash + " output: " + tx_out_idx);

  }

}
