package snowblossom.client;

import snowblossom.proto.Transaction;

public interface MonitorInterface
{
  public void onInbound(Transaction tx, int tx_out_idx);

  public void onOutbound(Transaction tx, int tx_in_idx);

}
