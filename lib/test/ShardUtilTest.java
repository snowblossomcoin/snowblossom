package lib.test;

import org.junit.Test;
import org.junit.Assert;
import java.util.*;

import snowblossom.lib.ShardUtil;

public class ShardUtilTest
{
  @Test
  public void testIdConversion()
  {

    LinkedList<Integer> expand_list = new LinkedList<>();

    TreeSet<Integer> all_shards = new TreeSet<>();

    all_shards.add(0);

    expand_list.add(0);

    for(int x = 0; x<10000; x++)
    {
      Collections.shuffle(expand_list);
      Assert.assertTrue(ShardUtil.isProperSet(expand_list));

      //System.out.println("Expand list: " + expand_list);
      testShardList(expand_list, all_shards);
      int shard = expand_list.pollFirst();
      if (shard > 100000000)
      {
        // avoid overflow
        expand_list.add(shard);
        continue;
      }

      Assert.assertFalse(ShardUtil.isProperSet(expand_list));


      List<Integer> childs = ShardUtil.getShardChildIds(shard);
      for(int c : childs)
      {
        Assert.assertFalse(all_shards.contains(c));
        all_shards.add(c);
        expand_list.add(c);
        Assert.assertEquals( shard, ShardUtil.getShardParentId(c) );
      }


    }
  }

  private void testShardList(List<Integer> active_shards, Set<Integer> all_shards)
  {
    TreeSet<Integer> claimed = new TreeSet<>();

    for(int s : active_shards)
    { 
      Set<Integer> claim = ShardUtil.getInheritSet(s);
      Assert.assertTrue(claim.contains(s));
      for(int v : claim)
      {
        Assert.assertFalse(claimed.contains(v));
        claimed.add(v);
      }
    }
    Assert.assertEquals(all_shards,claimed);

  }
  

}
