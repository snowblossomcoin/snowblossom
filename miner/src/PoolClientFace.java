package snowblossom.miner;

import snowblossom.proto.*;
import snowblossom.mining.proto.*;


public interface PoolClientFace
{
  public void stop();
  public boolean isTerminated();
  public void subscribe() throws Exception;
  
  public WorkUnit getWorkUnit();

  public SubmitReply submitWork(WorkUnit wu, BlockHeader header);
  


}
