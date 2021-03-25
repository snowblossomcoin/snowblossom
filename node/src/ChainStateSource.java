package snowblossom.node;

import java.util.Set;

import snowblossom.lib.NetworkParams;

public interface ChainStateSource
{
  public int getHeight();

  public NetworkParams getParams();

  public Set<Integer> getShardCoverSet();

}
