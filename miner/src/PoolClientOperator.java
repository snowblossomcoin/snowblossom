package snowblossom.miner;

import snowblossom.mining.proto.*;

/** 
 * For things that use a PoolClient and need to get called back by it
 */
public interface PoolClientOperator
{
  public void notifyNewBlock(int block_id);

  public void notifyNewWorkUnit(WorkUnit wu);

}
