package snowblossom.shackleton;

import net.minidev.json.JSONObject;
import net.minidev.json.JSONArray;

import java.util.Collection;
import java.util.HashMap;

import snowblossom.proto.BlockImportList;
import snowblossom.proto.BlockHeader;
import snowblossom.lib.ChainHash;

import com.google.protobuf.ByteString;


/**
 * Create a json output for a group of blocks
 */
public class GraphOutput
{
  public static JSONObject getGraph(Collection<BlockHeader> blocks)
  {
    JSONArray node_array = new JSONArray();
    JSONArray link_array = new JSONArray();

    HashMap<ChainHash, BlockHeader> block_map = new HashMap<>();
    HashMap<ChainHash, String> name_map = new HashMap<>();
    HashMap<ChainHash, Integer> id_map = new HashMap<>();

    int id=1;
    // Set nodes
    for(BlockHeader bh : blocks)
    {
      ChainHash hash = new ChainHash(bh.getSnowHash());
      block_map.put(hash, bh);
      String name = getName(bh);
      name_map.put(hash, name);
      id_map.put(hash, id);
      JSONObject node = new JSONObject();
      node.put("id", id);
      node.put("name", name);
      node.put("group","s" + bh.getShardId());
      node.put("shard", bh.getShardId());
      node.put("timestamp", bh.getTimestamp());
      node.put("height", bh.getBlockHeight());
      node_array.add(node);
      id++;
    }
  
    // Build links
    for(BlockHeader bh : blocks)
    {
      ChainHash hash = new ChainHash(bh.getSnowHash());
      ChainHash prev = new ChainHash(bh.getPrevBlockHash());
      int my_id = id_map.get(hash);

      if (id_map.containsKey(prev))
      {
        JSONObject link = new JSONObject();
        link.put("source", my_id);
        link.put("target", id_map.get(prev));
        link.put("parent", 1);
        link_array.add(link);
      }

      for(BlockImportList bil : bh.getShardImportMap().values())
      {
        for(ByteString bs : bil.getHeightMap().values())
        {
          ChainHash imp_hash = new ChainHash(bs);
          if (id_map.containsKey(imp_hash))
          {
            JSONObject link = new JSONObject();
            link.put("source", my_id);
            link.put("target", id_map.get(imp_hash));
            link.put("import", 1);
            link_array.add(link);

          }
        }
      }

    }
    

    JSONObject graph = new JSONObject();
    graph.put("nodes", node_array);
    graph.put("links", link_array);


    return graph;
  }

  public static String getName(BlockHeader bh)
  {
    ChainHash hash = new ChainHash(bh.getSnowHash());
    String hash_str = hash.toString().substring(0,12);
    return String.format("s%dh%d-%s", bh.getShardId(), bh.getBlockHeight(), hash_str);

  }

}

