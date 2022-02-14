package snowblossom.shackleton;

import com.google.protobuf.ByteString;
import duckutil.SoftLRUCache;
import java.util.Map;
import java.util.TreeMap;
import net.minidev.json.JSONObject;
import snowblossom.lib.*;
import snowblossom.proto.*;

public class StuffCache
{

  private Shackleton shackleton;
  private SoftLRUCache<ChainHash, BlockHeader> header_cache = new SoftLRUCache<>(10000);

  public StuffCache(Shackleton shackleton)
  {
    this.shackleton = shackleton;
  }


  public Map<Integer, BlockHeader> getNetShardHeaderMap(NodeStatus ns)
  {
    TreeMap<Integer, BlockHeader> out = new TreeMap<>();
    for(Map.Entry<Integer, ByteString> me : ns.getNetShardHeadMap().entrySet())
    {
      int shard = me.getKey();
      ChainHash hash = new ChainHash(me.getValue());
      BlockHeader bh = getBlockHeader(hash);
      out.put(shard,bh);
    }
    return out;
  }

  public BlockHeader getBlockHeader(ChainHash hash)
  {
    synchronized(header_cache)
    {
      if (header_cache.get(hash) != null) return header_cache.get(hash);
    }

    BlockHeader bh = shackleton.getStub().getBlockHeader(
    RequestBlockHeader.newBuilder().setBlockHash(hash.getBytes()).build());

    if (bh!=null)
    {
      synchronized(header_cache)
      {
        header_cache.put(hash, bh);
      }
    }

    return bh;
  }

}
