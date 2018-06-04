package node.test;

import org.junit.Assert;
import org.junit.Test;
import snowblossom.proto.BlockHeader;
import snowblossom.proto.BlockSummary;
import snowblossom.node.BlockIngestor;
import snowblossom.lib.BlockchainUtil;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.NetworkParamsTestnet;

import java.math.BigInteger;


public class BlockIngestorTest
{
  @Test
  public void testFirstBlockSummary()
  {
    NetworkParams params = new NetworkParamsTestnet();

		BlockHeader header = BlockHeader.newBuilder()
      .setTarget(BlockchainUtil.targetBigIntegerToBytes(params.getMaxTarget() ))
      .setTimestamp(System.currentTimeMillis())
      .build();

    BlockSummary prev_summary;
      prev_summary = BlockSummary.newBuilder().build();
    System.out.println(prev_summary);

		BlockSummary s = BlockchainUtil.getNewSummary(header, prev_summary, params, 1L);

    Assert.assertNotNull(s.getHeader());
    Assert.assertEquals("1024", s.getWorkSum());
    Assert.assertEquals(params.getMaxTarget().toString(), s.getTargetAverage());
    Assert.assertEquals(params.getBlockTimeTarget(), s.getBlocktimeAverageMs());
		
  }

  @Test
  public void testMagicBlockSummary()
  {
    NetworkParams params = new NetworkParamsTestnet();
    long time = System.currentTimeMillis();

    BigInteger using_target = params.getMaxTarget().divide(BigInteger.valueOf(2L));

		BlockHeader header = BlockHeader.newBuilder()
      .setTarget(BlockchainUtil.targetBigIntegerToBytes(using_target ))
      .setTimestamp(time)
      .build();

    long using_time = params.getBlockTimeTarget() / 2;

    BlockHeader prev_header = BlockHeader.newBuilder()
      .setTimestamp(time - using_time)
      .build();

    BlockSummary prev_summary;
      prev_summary = BlockSummary.newBuilder()
        .setWorkSum("1000")
        .setBlocktimeAverageMs(params.getBlockTimeTarget())
        .setTargetAverage(params.getMaxTarget().toString())
        .setHeader(prev_header)
        .build();
    System.out.println(prev_summary);

		BlockSummary s = BlockchainUtil.getNewSummary(header, prev_summary, params, 1L);

    BigInteger expected_target = params.getMaxTarget().multiply(BigInteger.valueOf(990L)).add(using_target.multiply(BigInteger.valueOf(10L))).divide(BigInteger.valueOf(1000L));
    long expected_time = (params.getBlockTimeTarget() * 990L + using_time * 10L) / 1000L;

    Assert.assertNotNull(s.getHeader());
    int work = 1000 + 1024 * 2;
    Assert.assertEquals("" + work, s.getWorkSum());
    Assert.assertEquals(expected_target.toString(), s.getTargetAverage());
    Assert.assertEquals(expected_time, s.getBlocktimeAverageMs());
		
  }

}


