package snowblossom;

import org.junit.Test;
import org.junit.Assert;

import com.google.protobuf.ByteString;
import org.apache.commons.codec.binary.Hex;

import snowblossom.proto.BlockSummary;
import snowblossom.proto.BlockHeader;


public class BlockIngestorTest
{
  @Test
  public void testFirstBlockSummary()
  {
    NetworkParams params = new NetworkParamsTestnet();

		BlockHeader header = BlockHeader.newBuilder()
      .setTarget(BlockchainUtil.targetLongToBytes( params.getMaxTarget() ))
      .setTimestamp(System.currentTimeMillis())
      .build();

    BlockSummary prev_summary;
      prev_summary = BlockSummary.newBuilder().build();
    System.out.println(prev_summary);

		BlockSummary s = BlockIngestor.getNewSummary(header, prev_summary, params);

    Assert.assertNotNull(s.getHeader());
    Assert.assertEquals(128, s.getWorkSum());
    Assert.assertEquals(params.getMaxTarget(), s.getTargetAverage());
    Assert.assertEquals(params.getBlockTimeTarget(), s.getBlocktimeAverageMs());
		
  }

  @Test
  public void testMagicBlockSummary()
  {
    NetworkParams params = new NetworkParamsTestnet();
    long time = System.currentTimeMillis();

    long using_target = params.getMaxTarget() / 2;

		BlockHeader header = BlockHeader.newBuilder()
      .setTarget(BlockchainUtil.targetLongToBytes( using_target ))
      .setTimestamp(time)
      .build();

    long using_time = params.getBlockTimeTarget() / 2;

    BlockHeader prev_header = BlockHeader.newBuilder()
      .setTimestamp(time - using_time)
      .build();

    BlockSummary prev_summary;
      prev_summary = BlockSummary.newBuilder()
        .setWorkSum(1000L)
        .setBlocktimeAverageMs(params.getBlockTimeTarget())
        .setTargetAverage(params.getMaxTarget())
        .setHeader(prev_header)
        .build();
    System.out.println(prev_summary);

		BlockSummary s = BlockIngestor.getNewSummary(header, prev_summary, params);

    long expected_target = (params.getMaxTarget() * 990L + using_target * 10L) / 1000L;
    long expected_time = (params.getBlockTimeTarget() * 990L + using_time * 10L) / 1000L;

    Assert.assertNotNull(s.getHeader());
    Assert.assertEquals(1000 + 128 * 2, s.getWorkSum());
    Assert.assertEquals(expected_target, s.getTargetAverage());
    Assert.assertEquals(expected_time, s.getBlocktimeAverageMs());
		
  }

}


