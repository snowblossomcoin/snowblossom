package lib.test;

import org.junit.Test;
import org.junit.Assert;
import java.util.*;

import snowblossom.lib.ShardUtil;
import com.google.common.collect.ImmutableList;
import snowblossom.proto.BlockHeader;
import snowblossom.proto.BlockImportList;
import snowblossom.lib.PowUtil;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.NetworkParamsRegtest;
import com.google.protobuf.ByteString;


public class ShardUtilTest
{
  @Test
  public void testIdConversion()
  {
    LinkedList<Integer> expand_list = new LinkedList<>();

    TreeSet<Integer> all_shards = new TreeSet<>();

    all_shards.add(0);

    expand_list.add(0);

    for(int x = 0; x<1000; x++)
    {
      Collections.shuffle(expand_list);
      Assert.assertTrue(ShardUtil.isProperSet(expand_list));

      //System.out.println("Expand list: " + expand_list);
      testShardList(expand_list, all_shards);
      int shard = expand_list.pollFirst();
      if (shard > 1000000)
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

    testShardShares(active_shards);

  }


  /** 
   * Tests that the list of shards given sums to 1.0
   */
  private void testShardShares(List<Integer> active_shards)
  {
    int lcm = 1 << 30;
    int sum = 0;

    LinkedList<Integer> divisors = new LinkedList<>();

    for(int s : active_shards)
    {
      int div = ShardUtil.getShardShareDivisor(s);
      sum += lcm / div;

      divisors.add(div);
    }

    Assert.assertEquals("Divs: " + divisors, lcm, sum);
  }


  @Test
  public void testBlockRewardNoShard()
  {
    NetworkParams params = new NetworkParamsRegtest();

    long reward = ShardUtil.getBlockReward(params, BlockHeader.newBuilder().build());
    Assert.assertEquals(PowUtil.getBlockReward(params, 0), reward);


  }

  public List<Integer> getGeneration(int n)
  {
    if (n==0) return ImmutableList.of(0);

    LinkedList<Integer> lst = new LinkedList<>();
    for(int s : getGeneration(n-1))
    {
      lst.addAll( ShardUtil.getShardChildIds(s));
    }
    return lst;
  }

  @Test
  public void testBlockRewardSum()
  {
    LinkedList< List<Integer> > shard_lists = new LinkedList<>();

    shard_lists.add(ImmutableList.of(0));
    
    shard_lists.add(ImmutableList.of(1,5,6));
    shard_lists.add(ImmutableList.of(3,4,5,6));
    shard_lists.add(ImmutableList.of(7,8,9,10,5,6));
    shard_lists.add(getGeneration(3));
    shard_lists.add(getGeneration(5)); //32 shards
    shard_lists.add(getGeneration(7)); //128 shards
    shard_lists.add(getGeneration(8)); //256 shards

    NetworkParams params = new NetworkParamsRegtest();
    long blocks_per_day = 86400 / (params.getBlockTimeTarget() / 1000);
    int blocks_per_year = (int)blocks_per_day * 365;
    int blocks_four_years = blocks_per_year * 4;

    Random rnd = new Random();

    for(List<Integer> shards : shard_lists)
    {
      ArrayList<BlockHeader.Builder> headers = new ArrayList<>();
      Assert.assertTrue(ShardUtil.isProperSet(shards));
      
      long expected_total = 0;
      List<Integer> height_list = new LinkedList<>();

      for(int h=blocks_four_years - 25; h<=blocks_four_years+25; h++)
      //for(int h=0; h<100; h++)
			{
        height_list.add(h);
        expected_total += PowUtil.getBlockReward(params, h);
      }
      System.out.println("Expected block reward: " + expected_total);

      for(int s : shards)
      {
        ArrayList<BlockHeader.Builder> shard_headers = new ArrayList<>();

        for(int h : height_list)
        {
          BlockHeader.Builder b = BlockHeader.newBuilder();
          b.setShardId(s);
          b.setBlockHeight(h);
          shard_headers.add(b);
        }

        // For every other shard and height, import that somewhere
        for(int o : shards)
        {
          if (s != o)
          {
            for(int h : height_list)
            {
              BlockHeader.Builder b = shard_headers.get( rnd.nextInt(shard_headers.size()));

              if (!b.getShardImportMap().containsKey(o))
              {
                b.putShardImport(o, BlockImportList.newBuilder().build() );
              }

              BlockImportList.Builder il = BlockImportList.newBuilder();
              il.mergeFrom(b.getShardImportMap().get(o));
              il.putHeightMap(h, ByteString.EMPTY);
              b.putShardImport(o, il.build());

            }
          }
        }

        headers.addAll(shard_headers);
			}

      long found_reward=0;
      for(BlockHeader.Builder b : headers)
      {
        found_reward+=ShardUtil.getBlockReward( params, b.build() );
      }

      // With only six digits after the decimal and a few shards going
      // We start to get some truncation errors so a tiny slice of the reward
      // is not issued.
      // The error is limited to at most one flake per block.
      // Note: even though we are losing some flakes, the calculation
      // is all integer math that will always be done the exact same way
      // so there is no concensus problem.
      long missing = expected_total - found_reward;
      System.out.println(shards.toString() + " missing " + missing + " from " + headers.size() + " blocks");
      Assert.assertTrue(missing < headers.size());

    }


  }
  

}
