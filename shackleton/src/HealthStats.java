package snowblossom.shackleton;

import com.google.protobuf.ByteString;
import duckutil.SoftLRUCache;
import java.util.Map;
import java.util.TreeMap;
import net.minidev.json.JSONObject;
import snowblossom.lib.*;
import snowblossom.proto.*;

public class HealthStats
{

  private Shackleton shackleton;

  public HealthStats(Shackleton shackleton)
  {
    this.shackleton = shackleton;
  }

  public JSONObject getHealthStats()
  {

    JSONObject stats = new JSONObject();

    stats.put("generated_time", System.currentTimeMillis());

    NodeStatus node_status = shackleton.getStub().getNodeStatus(QueryUtil.nr());
    Map<Integer, BlockHeader> heads = shackleton.getStuffCache().getNetShardHeaderMap(node_status);

    JSONObject shards = new JSONObject();
    stats.put("shards", shards);

    ChainStats global_1hr = new ChainStats(3600L * 1000L);
    ChainStats global_4hr = new ChainStats(4L * 3600L * 1000L);

    for(Map.Entry<Integer, BlockHeader> me : heads.entrySet())
    {
      shards.put("s" + me.getKey(), getShardData(me.getValue(), global_1hr, global_4hr));
    }

    global_1hr.populate(stats, "counts_1hr");
    global_4hr.populate(stats, "counts_4hr");

    return stats;

  }

  public JSONObject getShardData(BlockHeader start, ChainStats global_1hr, ChainStats global_4hr )
  {
    JSONObject shard_data = new JSONObject();

    shard_data.put("height", start.getBlockHeight());
    long age = System.currentTimeMillis() - start.getTimestamp();
    age = age /1000; // convert to seconds

    shard_data.put("age", age);
    BlockHeader current = start;

    ChainStats shard_1hr = new ChainStats(3600L * 1000L);
    ChainStats shard_4hr = new ChainStats(4L * 3600L * 1000L);


    while(global_4hr.addBlock(current))
    {
      global_1hr.addBlock(current);
      shard_1hr.addBlock(current);
      shard_4hr.addBlock(current);

      if (current.getBlockHeight() == 0) break;

      current = shackleton.getStuffCache().getBlockHeader(new ChainHash(current.getPrevBlockHash()));
    }

    shard_1hr.populate(shard_data, "counts_1hr");
    shard_4hr.populate(shard_data, "counts_4hr");

    return shard_data;

  }


  public class ChainStats
  {
    private final long start_time;
    private final long run_time;
    public ChainStats(long run_time)
    {
      this.run_time = run_time;
      this.start_time = System.currentTimeMillis() - run_time;
    }
    int blocks;
    int transactions;

    public boolean addBlock(BlockHeader header)
    {
      if (header == null) return false;
      if (header.getTimestamp() < start_time) return false;
      blocks++;
      transactions+=header.getTxCount();
      return true;

    }

    public void populate(JSONObject parent, String name)
    {
      JSONObject json = new JSONObject();
      
      json.put("block_count", blocks);
      json.put("tx_count", transactions);
      
      double tx_rate = transactions / (run_time / 1000.0);
      json.put("tx_per_sec", tx_rate);

      parent.put(name, json);
    }

  }

}
