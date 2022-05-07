package snowblossom.miner.plow;

import snowblossom.lib.*;
import snowblossom.mining.proto.*;
import snowblossom.proto.*;

public class BlockCompare implements Comparable<BlockCompare>
{
  private final BlockTemplate bt;

  public BlockCompare(BlockTemplate bt)
  {
    this.bt = bt;
  }

  public BlockTemplate getBlockTemplate(){return bt;}

  @Override
  public boolean equals(Object o)
  {
    throw new RuntimeException("Leave me alone");
  }

  @Override
  public int hashCode()
  {
    return bt.hashCode();
  }

  public double getRewardPerHash()
  {
    double diff = PowUtil.getDiffForTarget(
      BlockchainUtil.targetBytesToBigInteger(
        bt.getBlock().getHeader().getTarget()));
    double hashes = Math.pow(2.0, diff);

    Transaction coinbase = bt.getBlock().getTransactions(0);
    TransactionInner inner = TransactionUtil.getInner(coinbase);
    double reward = 0.0;
    for(TransactionOutput out : inner.getOutputsList())
    {
      reward += out.getValue();
    }

    return reward/hashes;

  }

  // First item is best item
  @Override
  public int compareTo(BlockCompare o)
  {
    if (bt.getAdvancesShard() > o.bt.getAdvancesShard()) return -1;
    if (bt.getAdvancesShard() < o.bt.getAdvancesShard()) return 1;

    if (getRewardPerHash() > o.getRewardPerHash()) return -1;
    if (getRewardPerHash() < o.getRewardPerHash()) return 1;

    if (bt.getBlock().getHeader().getBlockHeight() < o.bt.getBlock().getHeader().getBlockHeight()) return -1;
    if (bt.getBlock().getHeader().getBlockHeight() > o.bt.getBlock().getHeader().getBlockHeight()) return 1;

    if (bt.getBlock().getHeader().getTimestamp() > o.bt.getBlock().getHeader().getTimestamp()) return -1;
    if (bt.getBlock().getHeader().getTimestamp() < o.bt.getBlock().getHeader().getTimestamp()) return 1;

    return 0;
  }
}
