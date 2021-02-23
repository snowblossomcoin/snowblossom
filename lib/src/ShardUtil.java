package snowblossom.lib;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import duckutil.LRUCache;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import snowblossom.proto.BlockHeader;
import snowblossom.proto.BlockSummary;
import snowblossom.proto.BlockImportList;

public class ShardUtil
{

  public static final int REWARD_MATH_BASE_SHIFT=128;


  /*
   *         0
   *    1          2
   *  3   4     5     6
   * 7 8 9 10 11 12 13 14
   */

  /** 
   * parent must have exactly two children
   * first parent is shard zero (0)
   * there must be no duplicates (each child must have one parent)
   * ideally, all shard numbers would be used, but not required
   */
  public static int getShardChildIdLeft(int parent_id)
  {
    return parent_id * 2 + 1;
  }
  public static int getShardChildIdRight(int parent_id)
  {
    return parent_id * 2 + 2;
  }
  public static List<Integer> getShardChildIds(int parent_id)
  {
    return ImmutableList.of( getShardChildIdLeft(parent_id), getShardChildIdRight(parent_id) );
  }

  /** When children shards are created, the left shard (the odd one)
   inherits things that were for the parent. */
  public static Set<Integer> getInheritSet(int shard_id)
  {
    int v = shard_id;
    TreeSet<Integer> inherit_set = new TreeSet<>();
    while(true)
    {
      inherit_set.add(v);
      if (v % 2 == 0 ) break;
      v = getShardParentId(v);
    }

    return inherit_set;
  }


  private static LRUCache<String, Set<Integer> > cover_cache = new LRUCache<>(5000);
  
  public static Set<Integer> getCoverSet(int shard_id, NetworkParams params)
  {
    return getCoverSet(shard_id, params.getMaxShardId());
  }
  
  /**
   * return the set of all shard IDs that should be imported with this shard.
   * this includes the inherit set from parent shards (as appropriate),
   * this shard itself and any children shards up to the max_shard_id.
   */
  public static Set<Integer> getCoverSet(int shard_id, int max_shard_id)
  {
    String key = "" + shard_id + "," + max_shard_id;
    synchronized(cover_cache)
    {
      if (cover_cache.containsKey(key)) return cover_cache.get(key);
    }

    TreeSet<Integer> cover_set = new TreeSet<>();

    cover_set.addAll(getInheritSet(shard_id));
    cover_set.addAll(getChildrenRecursive(shard_id, max_shard_id));

    ImmutableSet<Integer> im_set = ImmutableSet.copyOf(cover_set);
    synchronized(cover_cache)
    {
      cover_cache.put(key, im_set);
    }

    return cover_set;

  }

  public static Set<Integer> getChildrenRecursive(int shard_id, int max_shard_id)
  {
    TreeSet<Integer> s = new TreeSet<>();

    if (shard_id <= max_shard_id)
    {
      s.add(shard_id);
      for(int c : getShardChildIds(shard_id) )
      {
        s.addAll( getChildrenRecursive(c, max_shard_id) );
      }
    }

    return s;
  }

  /**
   * reverse getShardChildId()
   */
  public static int getShardParentId(int child_id)
  {
    if (child_id % 2 == 0) return (child_id - 2) / 2;
    return (child_id - 1) / 2;
  }


  /**
   * True iff the set of shards given is a proper set, 
   * meaning these leaf nodes covers the entire tree with no gaps (no missing leafs)
   * and no duplicates (meaning no internal tree nodes)
   */
  public static boolean isProperSet(Collection<Integer> shards)
  {
    HashSet<Integer> leafs = new HashSet<>();
    leafs.addAll(shards);

    // All shards are unique
    if (leafs.size() != shards.size()) return false;

    // We desend tree on all branches
    if (!recursiveSetCheck(0, leafs)) return false;

    // Doing that tree desend gives us no remaining leafs
    return (leafs.size() == 0);
  }
  private static boolean recursiveSetCheck(int shard, Set<Integer> leafs)
  {
    if (shard > 2000000000) return false;
    if (leafs.contains(shard))
    {
      leafs.remove(shard);
      return true;
    }
    else
    {
      for(int c : getShardChildIds(shard))
      {
        if (!recursiveSetCheck(c,leafs)) return false;
      }
      return true;
    }

  }

  /** Return the number of generations of this shard.
   * The block reward should be 1 / pow(2, generation_number)
   * Shard zero is generation zero
   * also can be used as a bit shift value
   */
  public static int getShardGeneration(int shard_id)
  {
    // Ok, I know there is going to be a better way to do this
    // but I don't want to screw it up, so stupid on
    if (shard_id == 0) return 0;
    return getShardGeneration(getShardParentId(shard_id)) + 1;
  }

  /**
   * The block reward for this shard should be 1/divisor
   */
  public static int getShardShareDivisor(int shard_id)
  {
    return 1 << getShardGeneration(shard_id);
  }


  /**
   * Get Block Reward for this shard block not including fees
   */ 
  public static long getBlockReward(NetworkParams params, BlockHeader header)
  {
    BigInteger reward_sum = BigInteger.ZERO;

    // We need to go higher percision math but still with integers
    // So we use BigInteger and shift everything way to the left to give
    // us some wiggle room.

    BigInteger general_block_reward = BigInteger.valueOf(PowUtil.getBlockReward(params, header.getBlockHeight() )).shiftLeft(REWARD_MATH_BASE_SHIFT);
    BigInteger block_reward_direct_faction = general_block_reward.shiftRight(2).multiply( BigInteger.valueOf(3L));
    BigInteger block_reward_indirect_faction = general_block_reward.shiftRight(2);

    int shard_gen = getShardGeneration(header.getShardId());

    // Direct reward for this block
    reward_sum = reward_sum.add( block_reward_direct_faction.shiftRight(shard_gen ) );

    // Indirect reward for self
    reward_sum = reward_sum.add(block_reward_indirect_faction.shiftRight(shard_gen*2));

    for(Map.Entry<Integer, BlockImportList> me : header.getShardImportMap().entrySet())
    {
      reward_sum = reward_sum.add(getImportReward(params, header.getShardId(), me.getKey(), me.getValue()));
    }

    // Remove the shift and return
    return reward_sum.shiftRight(REWARD_MATH_BASE_SHIFT).longValue();
  }

  /**
   *
   * @param params NetworkParemters
   * @param src_shard the shard id of the block including these other blocks
   * @param shard the shard id of the blocks being imported
   * @param import_list the list of imported headers
   * @return number of snow flakes, shifted left by REWARD_MATH_BASE_SHIFT
   */
  public static BigInteger getImportReward(NetworkParams params, int src_shard, int shard, BlockImportList import_list)
  {
    BigInteger reward_sum = BigInteger.ZERO;
    int shard_gen = getShardGeneration(shard);
    int src_shard_gen = getShardGeneration(src_shard);
    
    for(int height : import_list.getHeightMap().keySet())
    {
      BigInteger general_block_reward = BigInteger.valueOf(PowUtil.getBlockReward(params, height )).shiftLeft(REWARD_MATH_BASE_SHIFT);
      BigInteger block_reward_indirect_faction = general_block_reward.shiftRight(2);

      BigInteger import_reward = block_reward_indirect_faction.shiftRight(shard_gen + src_shard_gen);

      reward_sum=reward_sum.add(import_reward);
    }

    return reward_sum;
  }

  /**
   * If this returns true, it indicates that the block the summary is about 
   * must be the last block in the shard it is part of and its children must
   * be two new shards.
   */
  public static boolean shardSplit(BlockSummary summary, NetworkParams params)
  {
		if (summary.getHeader().getVersion() == 2)
    if (summary.getTxSizeAverage() > params.getShardForkThreshold())
    if (summary.getShardLength() >= params.getMinShardLength())
    {
      return true;
    }
    return false;

  }

}
