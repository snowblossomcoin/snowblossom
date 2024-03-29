package lib.test;

import java.math.BigInteger;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;
import snowblossom.lib.BlockchainUtil;
import snowblossom.lib.Globals;
import snowblossom.lib.HexUtil;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.NetworkParamsProd;
import snowblossom.lib.NetworkParamsRegtest;
import snowblossom.lib.PowUtil;
import java.util.LinkedList;
import snowblossom.proto.*;

public class PowUtilTest
{

  public PowUtilTest()
  {
    Globals.addCryptoProvider();
  }
  @Test
  public void testCalcNextTargetInitial()
  {
    BlockSummary bs = BlockSummary.newBuilder().build();

    NetworkParams params = new NetworkParamsRegtest();

    BigInteger target = PowUtil.calcNextTarget(bs, params, System.currentTimeMillis());

    Assert.assertEquals(params.getMaxTarget(), target);
  }


  @Test
  public void testCalcNextTargetDecreasing()
  {
    NetworkParams params = new NetworkParamsRegtest();
    long time = System.currentTimeMillis();

    BlockHeader prev_header = BlockHeader.newBuilder()
      .setTimestamp(time - params.getBlockTimeTarget() / 2)
      .build();

    BlockSummary bs = BlockSummary.newBuilder()
      .setHeader(prev_header)
      .setTargetAverage(params.getMaxTarget().toString())
      .setBlocktimeAverageMs(params.getBlockTimeTarget())
      .build();


    BigInteger target = PowUtil.calcNextTarget(bs, params, System.currentTimeMillis());

    Assert.assertTrue(target.compareTo(params.getMaxTarget()) < 0);

  }

  @Test
  public void testCalcNextTargetDecreasingFromAvg()
  {
    NetworkParams params = new NetworkParamsRegtest();
    long time = System.currentTimeMillis();

    // block solved fast
    BlockHeader prev_header = BlockHeader.newBuilder()
      .setTimestamp(time - params.getBlockTimeTarget() / 2)
      .build();

    BigInteger average_target = params.getMaxTarget().divide(BigInteger.valueOf(100L));

    BlockSummary bs = BlockSummary.newBuilder()
      .setHeader(prev_header)
      .setTargetAverage(average_target.toString())
      .setBlocktimeAverageMs(params.getBlockTimeTarget())
      .build();

    BigInteger target = PowUtil.calcNextTarget(bs, params, System.currentTimeMillis());

    Assert.assertTrue(target.compareTo(average_target) < 0);
  }

  @Test
  public void testCalcNextTargetIncreasingFromAvg()
  {
    NetworkParams params = new NetworkParamsRegtest();
    long time = System.currentTimeMillis();
  
    //block solved slow
    BlockHeader prev_header = BlockHeader.newBuilder()
      .setTimestamp(time - params.getBlockTimeTarget() * 2)
      .build();

    BigInteger average_target = params.getMaxTarget().divide(BigInteger.valueOf(100));

    BlockSummary bs = BlockSummary.newBuilder()
      .setHeader(prev_header)
      .setTargetAverage(average_target.toString())
      .setBlocktimeAverageMs(params.getBlockTimeTarget())
      .build();


    BigInteger target = PowUtil.calcNextTarget(bs, params, System.currentTimeMillis());
    System.out.println("Old: " + average_target + " new: " + target);

    Assert.assertTrue(target.compareTo(average_target) > 0);

  }

  @Test
  public void testStability()
  {
    NetworkParams params = new NetworkParamsRegtest();
    long time = 1000000000L;
    BigInteger target = params.getMaxTarget();
    double simulated_solve_rate = 100; //per ms

    BlockSummary bs = BlockSummary.newBuilder().build();

    for(int i=0; i<10000; i++)
    {
      long last_time = time;
      
      target = PowUtil.calcNextTarget(bs, params, time);

      double tdiff = PowUtil.getDiffForTarget(target);
      double work = Math.pow(2, tdiff);
      long solve_time =(long) (work / simulated_solve_rate);

      if (solve_time <= 0) solve_time=1;
      time = time + solve_time;

      BlockHeader header = BlockHeader.newBuilder()
        .setTarget(BlockchainUtil.targetBigIntegerToBytes(target))
        .setTimestamp(time)
        .build();

      bs = BlockchainUtil.getNewSummary(header, bs, params, 1L, 600L, new LinkedList());

      Assert.assertEquals(time, bs.getHeader().getTimestamp());

    }

    double diff = Math.abs(bs.getBlocktimeAverageMs() - params.getBlockTimeTarget());
    diff = diff / params.getBlockTimeTarget();

    Assert.assertTrue(String.format("Diff: %f", diff), diff < 0.02);

    // increase speed

    simulated_solve_rate = 5;
    for(int i=0; i<10000; i++)
    {
      long last_time = time;
      
      target = PowUtil.calcNextTarget(bs, params, time);

      double tdiff = PowUtil.getDiffForTarget(target);
      double work = Math.pow(2, tdiff);
      long solve_time =(long) (work / simulated_solve_rate);

      if (solve_time <= 0) solve_time=1;
      time = time + solve_time;

      BlockHeader header = BlockHeader.newBuilder()
        .setTarget(BlockchainUtil.targetBigIntegerToBytes(target))
        .setTimestamp(time)
        .build();

      bs = BlockchainUtil.getNewSummary(header, bs, params, 1L, 600L, new LinkedList());

      Assert.assertEquals(time, bs.getHeader().getTimestamp());

    }


    diff = Math.abs(bs.getBlocktimeAverageMs() - params.getBlockTimeTarget());
    diff = diff / params.getBlockTimeTarget();

    Assert.assertTrue(diff < 0.02);



    //Assert.fail();

  }


  @Test
  public void testReward()
  {
    NetworkParams params = new NetworkParamsProd();
    int n = 4 * 365 * 144;
    long reward = 50000000;

    for(int b=1; b<10; b++)
    {
      Assert.assertEquals(reward, PowUtil.getBlockReward(params, n*b - 1));
      reward /= 2;
      Assert.assertEquals(reward, PowUtil.getBlockReward(params, n*b));
    }
  }

  @Test
  public void testNextWordIndex()
  {
    byte[] context=new byte[32];
    for(int i=0; i<32; i++) context[i]=(byte)i;

    long v = PowUtil.getNextSnowFieldIndex(context, 1024L*1024L*1024L*32L);

    Assert.assertEquals(19323217346L, v);

  }

  @Test
  public void testGetNextContext()
  {
    Random rnd=new Random(9182L);
    byte[] context=new byte[32];
    byte[] word=new byte[32];
    rnd.nextBytes(context);
    rnd.nextBytes(word);

    byte[] new_context = PowUtil.getNextContext(context, word);

    Assert.assertEquals("db176f021b168412a05904e7e729a072943cc8a4f266a4579654b8aa0fecfb8b", HexUtil.getHexString(new_context));


  }


}
