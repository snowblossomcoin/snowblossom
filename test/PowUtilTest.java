package snowblossom;

import org.junit.Test;
import org.junit.Assert;

import com.google.protobuf.ByteString;
import org.apache.commons.codec.binary.Hex;

import snowblossom.proto.BlockSummary;
import snowblossom.proto.BlockHeader;


public class PowUtilTest
{
  @Test
  public void testCalcNextTargetInitial()
  {
    BlockSummary bs = BlockSummary.newBuilder().build();

    NetworkParams params = new NetworkParamsTestnet();

    long target = PowUtil.calcNextTarget(bs, params, System.currentTimeMillis());

    Assert.assertEquals(params.getMaxTarget(), target);
    
  }


  @Test
  public void testCalcNextTargetDecreasing()
  {
    NetworkParams params = new NetworkParamsTestnet();
    long time = System.currentTimeMillis();

    BlockHeader prev_header = BlockHeader.newBuilder()
      .setTimestamp(time - params.getBlockTimeTarget() / 2)
      .build();

    BlockSummary bs = BlockSummary.newBuilder()
      .setHeader(prev_header)
      .setTargetAverage(params.getMaxTarget())
      .setBlocktimeAverageMs(params.getBlockTimeTarget())
      .build();


    long target = PowUtil.calcNextTarget(bs, params, System.currentTimeMillis());

    Assert.assertTrue(target < params.getMaxTarget());

  }

  @Test
  public void testCalcNextTargetDecreasingFromAvg()
  {
    NetworkParams params = new NetworkParamsTestnet();
    long time = System.currentTimeMillis();

    // block solved fast
    BlockHeader prev_header = BlockHeader.newBuilder()
      .setTimestamp(time - params.getBlockTimeTarget() / 2)
      .build();

    long average_target = params.getMaxTarget() / 100;

    BlockSummary bs = BlockSummary.newBuilder()
      .setHeader(prev_header)
      .setTargetAverage(average_target)
      .setBlocktimeAverageMs(params.getBlockTimeTarget())
      .build();

    long target = PowUtil.calcNextTarget(bs, params, System.currentTimeMillis());

    Assert.assertTrue(target < average_target);
  }

  @Test
  public void testCalcNextTargetIncreasingFromAvg()
  {
    NetworkParams params = new NetworkParamsTestnet();
    long time = System.currentTimeMillis();
  
    //block solved slow
    BlockHeader prev_header = BlockHeader.newBuilder()
      .setTimestamp(time - params.getBlockTimeTarget() * 2)
      .build();

    long average_target = params.getMaxTarget() / 100;

    BlockSummary bs = BlockSummary.newBuilder()
      .setHeader(prev_header)
      .setTargetAverage(average_target)
      .setBlocktimeAverageMs(params.getBlockTimeTarget())
      .build();


    long target = PowUtil.calcNextTarget(bs, params, System.currentTimeMillis());
    System.out.println("Old: " + average_target + " new: " + target);

    Assert.assertTrue(target > average_target);

  }

  @Test
  public void testStability()
  {
    NetworkParams params = new NetworkParamsTestnet();
    long time = 1000000000L;
    long target = params.getMaxTarget();
    double simulated_solve_rate = 1; //per ms

    BlockSummary bs = BlockSummary.newBuilder().build();

    for(int i=0; i<10000; i++)
    {
      long last_time = time;
      
      target = PowUtil.calcNextTarget(bs, params, time);

      double tar_double = target;
      double max = Long.MAX_VALUE;
      double solve_prob = tar_double / max;
      double solve_work = max / tar_double;

      long solve_time =(long) (solve_work / simulated_solve_rate);
      if (solve_time <= 0) solve_time=1;
      time = time + solve_time;

      BlockHeader header = BlockHeader.newBuilder()
        .setTarget(BlockchainUtil.targetLongToBytes(target))
        .setTimestamp(time)
        .build();

      bs = BlockIngestor.getNewSummary(header, bs, params);

      Assert.assertEquals(time, bs.getHeader().getTimestamp());

    }

    double diff = Math.abs(bs.getBlocktimeAverageMs() - params.getBlockTimeTarget());
    diff = diff / params.getBlockTimeTarget();

    Assert.assertTrue(diff < 0.01);

    // increase speed

    simulated_solve_rate = 5;
    for(int i=0; i<10000; i++)
    {
      long last_time = time;
      
      target = PowUtil.calcNextTarget(bs, params, time);

      double tar_double = target;
      double max = Long.MAX_VALUE;
      double solve_prob = tar_double / max;
      double solve_work = max / tar_double;

      long solve_time =(long) (solve_work / simulated_solve_rate);
      if (solve_time <= 0) solve_time=1;
      time = time + solve_time;

      BlockHeader header = BlockHeader.newBuilder()
        .setTarget(BlockchainUtil.targetLongToBytes(target))
        .setTimestamp(time)
        .build();

      bs = BlockIngestor.getNewSummary(header, bs, params);

      Assert.assertEquals(time, bs.getHeader().getTimestamp());

    }


    diff = Math.abs(bs.getBlocktimeAverageMs() - params.getBlockTimeTarget());
    diff = diff / params.getBlockTimeTarget();

    Assert.assertTrue(diff < 0.01);



    //Assert.fail();

  }



}


