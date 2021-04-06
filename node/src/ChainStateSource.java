package snowblossom.node;

import java.util.Set;

import snowblossom.lib.NetworkParams;

public interface ChainStateSource
{
  public int getHeight();
  public int getShardId();

  public NetworkParams getParams();

  public Set<Integer> getShardCoverSet();

}
