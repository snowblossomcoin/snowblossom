package snowblossom.node;


import java.util.TreeMap;
import snowblossom.lib.ChainHash;

public class BlockHeightCache
{
  private TreeMap<Integer, ChainHash> map = new TreeMap<>();
  private SnowBlossomNode node;

  public BlockHeightCache(SnowBlossomNode node)
  {
    this.node = node;
  }

  public synchronized void setHash(int height, ChainHash hash)
  {
    map.put(height, hash);
  }

  public synchronized ChainHash getHash(int height)
  {
    ChainHash hash = map.get(height);
    if (hash == null)
    {
      hash = node.getDB().getBlockHashAtHeight(height);
      map.put(height, hash);
    }
    return hash;
  }
    
}
