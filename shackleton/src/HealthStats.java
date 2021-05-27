package snowblossom.shackleton;

import net.minidev.json.JSONObject;

import snowblossom.lib.*;                                                                                                                                     
import snowblossom.proto.*;

import duckutil.SoftLRUCache;
import java.util.Map;
import java.util.TreeMap;
import com.google.protobuf.ByteString;

public class HealthStats
{

  private Shackleton shackleton;
  private SoftLRUCache<ChainHash, BlockHeader> header_cache = new SoftLRUCache<>(10000);

  public HealthStats(Shackleton shackleton)
  {
    this.shackleton = shackleton;
  }

  public JSONObject getHealthStats()
  {

    JSONObject stats = new JSONObject();

    stats.put("generated_time", System.currentTimeMillis());

    NodeStatus node_status = shackleton.getStub().getNodeStatus(QueryUtil.nr());
    Map<Integer, BlockHeader> heads = getShardHeaderMap(node_status);

    JSONObject shards = new JSONObject();
    stats.put("shards", shards);
    for(Map.Entry<Integer, BlockHeader> me : heads.entrySet())
    {
      shards.put("s" + me.getKey(), getShardData(me.getValue()));
    }

    return stats;

  }

  public JSONObject getShardData(BlockHeader start)
  {
    JSONObject shard_data = new JSONObject();

    shard_data.put("height", start.getBlockHeight());


    return shard_data;

  }

  private Map<Integer, BlockHeader> getShardHeaderMap(NodeStatus ns)                                                                                        
  {                                                                                                                                                           
    TreeMap<Integer, BlockHeader> out = new TreeMap<>();                                                                                                     
    for(Map.Entry<Integer, ByteString> me : ns.getShardHeadMap().entrySet())                                                                                  
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
