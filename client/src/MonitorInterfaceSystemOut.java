package snowblossom.client;

import snowblossom.lib.ChainHash;
import snowblossom.proto.Transaction;

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
