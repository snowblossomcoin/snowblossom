package snowblossom.node;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import org.junit.Assert;
import snowblossom.lib.*;
import snowblossom.proto.*;

public class GoldSetFinder
{
  private ByteString GOLD_MAP_KEY = ByteString.copyFrom("gs_find".getBytes());
  private SnowBlossomNode node;
  private static final Logger logger = Logger.getLogger("snowblossom.node");

  public GoldSetFinder(SnowBlossomNode node)
  {
    this.node = node;
  }

  private Map<Integer, BlockHeader> loadGoldSet()
  {
    GoldSet gs = node.getDB().getGoldSetMap().get(GOLD_MAP_KEY);
    if (gs == null) return null;

    Map<Integer, BlockHeader> gold = new TreeMap<>();

    for(Map.Entry<Integer,ByteString> me : gs.getShardToHashMap().entrySet())
    {
      int shard = me.getKey();
      ChainHash hash = new ChainHash(me.getValue());

      BlockHeader h = node.getForgeInfo().getHeader(hash);
      if (h == null)
      {
        logger.warning(String.format("Unable to load gold set, header not found: %d %s", shard, hash.toString()));
        return null;
      }
      gold.put(shard, h);
    }
    return gold;
  }

   /**
   * Returns the highest value set of blocks that all work together that we can find
   */
  public Map<Integer, BlockHeader> getGoldenSet(int depth)
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("GoldSetFinder.getGoldenSet(" + depth + ")"))
    {
     
      System.out.println("Finding golden set - depth " + depth);
      Map<Integer,Set<ChainHash> > head_shards = new TreeMap<Integer, Set<ChainHash> >();

      Map<Integer, BlockHeader> network_shards = node.getForgeInfo().getNetworkActiveShards();

      System.out.println("Network active shards: " + network_shards.keySet());


      int total_sources = 0;
      for(Map.Entry<Integer, BlockHeader> me : network_shards.entrySet())
      {
        Set<ChainHash> hs = node.getForgeInfo().getBlocksAround( new ChainHash(me.getValue().getSnowHash()), depth, me.getKey());
        total_sources += hs.size();
        head_shards.put(me.getKey(), hs);
      }

      System.out.println("Gold search block count: " + total_sources);

      Map<Integer, BlockHeader> gold = getGoldenSetRecursive(head_shards, ImmutableMap.of(), false, ImmutableMap.of());

      if (gold == null)
      {
        System.out.println("No gold set - depth " + depth);
        return null;
      }

      //gold = goldPrune(goldUpgrade(gold));

      HashSet<ChainHash> gold_set = new HashSet<ChainHash>();

      for(BlockHeader bh : gold.values())
      {
        gold_set.add(new ChainHash(bh.getSnowHash()));
      }

      System.out.println("GoldSetFinder set found: " + getSetDescription(gold));

      return gold;
    }
  }

  private String getSetDescription(Map<Integer, BlockHeader> map)
  {
    StringBuilder sb = new StringBuilder();

    sb.append("set{");
    for(Map.Entry<Integer, BlockHeader> me : map.entrySet())
    {
      sb.append(
        String.format("[s%d/%d h%d %s]",
          me.getKey(),
          me.getValue().getShardId(),
          me.getValue().getBlockHeight(),
          new ChainHash(me.getValue().getSnowHash()).toString()
        )
      );
    }
    sb.append("}");
    return sb.toString();

  }
 

 /**
   * Find blocks for the remaining_shards that don't conflict with anything in known_map.
   * @param remaining_shards - shards left to add in and the possible blocks to use in those shards
   * @param known_map - current set of blocks we are locking to
   * @param short_cut - if true, return first valid result not best
   * @param current_selections - current mapping of shard heads
   * @return the map of shards to headers that is the best
   */
  private Map<Integer, BlockHeader> getGoldenSetRecursive(Map<Integer, Set<ChainHash> > remaining_shards, 
    Map<String, ChainHash> known_map, boolean short_cut, Map<Integer, BlockHeader> current_selections)
  {
    // TODO need to handle the case where there are new children shards that should be ignored

    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("GoldSetFinder.getGoldenSetRecursive"))
    {
      if (remaining_shards.size() == 0) return new TreeMap<Integer, BlockHeader>();

      TreeMap<Integer, Set<ChainHash> > rem_shards_tree = new TreeMap<>();
      rem_shards_tree.putAll(remaining_shards);

      int shard_id = rem_shards_tree.firstEntry().getKey();
      Set<ChainHash> shard_blocks = rem_shards_tree.pollFirstEntry().getValue();

      BigInteger best_solution_val = BigInteger.valueOf(-1L);
      Map<Integer, BlockHeader> best_solution = null;

      for(ChainHash hash : shard_blocks)
      {
        BlockHeader bs = node.getForgeInfo().getHeader(hash);
        Assert.assertEquals(shard_id, bs.getShardId());
        Map<String, ChainHash> block_known_map = checkCollisions( known_map, hash);

        // If this block doesn't collide
        if (block_known_map != null)
        {
          // Add in blocker for self on next block to make sure no one has ideas for it
          // TODO - not always correct, this shard could be just about for split
          // But if we did track it correctly, then this would fail because then we are trying to not
          // include a block in the gold set that has children also in the gold set
          // so we might want to not include this shard, but if it is here the shard forking is recent
          // and we might need to reorg away from the forking so can't quite do that.
          // So we leave it as basically double bug cancel out but working
          if (Validation.checkCollisionsNT( block_known_map, shard_id, bs.getBlockHeight()+1, ChainHash.ZERO_HASH))
          {
            Map<Integer, BlockHeader> sub_selections = new OverlayMap<>(current_selections, false);
            sub_selections.put(shard_id, bs);
            Map<Integer, BlockHeader> sub_solution = getGoldenSetRecursive( rem_shards_tree, block_known_map, short_cut, sub_selections );
            if (sub_solution != null)
            {
              //System.out.println("Checking shard " + shard_id + " hash " + hash + " found sol");
              sub_solution.put(shard_id, bs);
              BigInteger val_sum = BigInteger.ZERO;
              for(BlockHeader sub_h : sub_solution.values())
              {
                val_sum = val_sum.add( BigInteger.valueOf(sub_h.getBlockHeight()) );
                //val_sum = val_sum.add( BlockchainUtil.readInteger( bs_sub.getWorkSum() ));
              }
          
              if (val_sum.compareTo(best_solution_val) > 0)
              {
                best_solution = sub_solution;
                best_solution_val = val_sum;
                if (short_cut)
                {
                  return best_solution; // short circuit
                }
              }
            }
            else
            {

              //System.out.println("Checking shard " + shard_id + " hash " + hash + " no sol");
            }
          }
        }
      }

      // if my parent is present, try with and without me
      if (best_solution == null)
      if (shard_id > 0) 
      if (current_selections.containsKey( ShardUtil.getShardParentId(shard_id)))
      {
        Map<Integer, BlockHeader> sub_solution = getGoldenSetRecursive(rem_shards_tree, known_map, short_cut, current_selections);

        if (sub_solution != null)
        {
          BigInteger val_sum = BigInteger.ZERO;
          for(BlockHeader sub_h : sub_solution.values())
          {
            val_sum = val_sum.add( BigInteger.valueOf(sub_h.getBlockHeight()) );
          }
            
          if (val_sum.compareTo(best_solution_val) > 0)
          {
            best_solution = sub_solution;
            best_solution_val = val_sum;
            if (short_cut)
            {
              return best_solution; // short circuit
            }
          }
        }

      }

      return best_solution;
    }
  }

  private Map<String, ChainHash> checkCollisions(Map<String, ChainHash> known_map_in, ChainHash hash)
  {

    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("GoldSetFinder.checkCollisions(m,hash)"))
    {
      OverlayMap<String, ChainHash> known_map = new OverlayMap<>(known_map_in, true);
      
      for(Map.Entry<String, ChainHash> me : node.getForgeInfo().getInclusionMap(hash).entrySet())
      {
        String key = me.getKey();
        ChainHash h = me.getValue();
        if (known_map.containsKey(key))
        {
          if (!known_map.get(key).equals(h)) return null;
        }
        else
        {
          known_map.put(key,h);
        }
      }
      return known_map;
    }

  }

  /**
   * Take a given gold set, remove any parents where both children are represented in the set
   */                                                                                                                     private Map<Integer, BlockHeader> goldPrune( Map<Integer, BlockHeader> gold_in)
  {
    Map<Integer, BlockHeader> out_map = new TreeMap<>();

    for(int s : ImmutableSet.copyOf(gold_in.keySet()))
    {
      int count = 0;
      for(int c : ShardUtil.getShardChildIds(s))
      {
        if (gold_in.containsKey(c)) count++;
      }
      if (count < 2)
      {
        out_map.put(s, gold_in.get(s));
      }
    }
    return out_map;

  }

  private Map<Integer, BlockHeader> goldUpgrade( Map<Integer, BlockHeader> gold_in)
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("GoldSetFinder.goldUpgrade"))
    {
      Map<Integer,Set<ChainHash> > head_shards = new TreeMap<Integer, Set<ChainHash> >();
      // Existing shards
      for(int s : gold_in.keySet())
      {
        head_shards.put(s, node.getForgeInfo().climb( new ChainHash(gold_in.get(s).getSnowHash()), s));
      }

      // Maybe merge in new children shards
      for(int s : gold_in.keySet())
      {
        for(int c : ShardUtil.getShardChildIds(s))
        {
          if (!head_shards.containsKey(c))
          if (node.getForgeInfo().getShardHead(c)!=null)
          {
            BlockHeader h = node.getForgeInfo().getShardHead(c);
            Set<ChainHash> hs = node.getForgeInfo().getBlocksAround( new ChainHash(h.getSnowHash()),1, c);
            head_shards.put(c, hs);
          }
        }
      }
      return getGoldenSetRecursive(head_shards, ImmutableMap.of(), false, ImmutableMap.of());
    }
  }

  public Map<String, ChainHash> getKnownMap(Map<Integer, BlockHeader> set)
  {
    HashMap<String, ChainHash> map = new HashMap<>(256,0.5f);

    for(BlockHeader bh : set.values())
    {
      ChainHash hash = new ChainHash(bh.getSnowHash());
    
      map.putAll(node.getForgeInfo().getInclusionMap(hash));
    }

    return map;
  }


}
