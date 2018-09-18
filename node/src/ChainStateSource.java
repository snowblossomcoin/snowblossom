package snowblossom.node;

import snowblossom.lib.NetworkParams;

public interface ChainStateSource
{
  public int getHeight();

  public NetworkParams getParams();

}
