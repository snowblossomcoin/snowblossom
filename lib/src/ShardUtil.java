package snowblossom.lib;

import java.util.List;
import com.google.common.collect.ImmutableList;
import java.util.TreeSet;
import java.util.Set;
import java.util.Collection;
import java.util.HashSet;


public class ShardUtil
{

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

}
