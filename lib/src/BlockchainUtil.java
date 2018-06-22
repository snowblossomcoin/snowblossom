package snowblossom.lib;

import com.google.protobuf.ByteString;
import org.junit.Assert;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import snowblossom.proto.*;


public class BlockchainUtil
{
  public static BigInteger targetBytesToBigInteger(ByteString bytes)
  {
    Assert.assertEquals(Globals.TARGET_LENGTH, bytes.size());

    return new BigInteger(1, bytes.toByteArray());

  }

  public static ByteString targetBigIntegerToBytes(BigInteger target)
  {
    ByteBuffer bb = ByteBuffer.allocate(Globals.TARGET_LENGTH);

    byte[] data = target.toByteArray();
    int zeros = Globals.TARGET_LENGTH - data.length;

    for (int i = 0; i < zeros; i++)
    {
      byte z = 0;
      bb.put(z);
    }
    bb.put(data);

    return ByteString.copyFrom(bb.array());
  }

  public static BigInteger getTargetForDiff(int n)
  {
    return BigInteger.ONE.shiftLeft(256 - n);
  }

  public static BigInteger readInteger(String s)
  {
    if (s.length() == 0) return BigInteger.ZERO;

    return new BigInteger(s);
  }

  public static BlockSummary getNewSummary(BlockHeader header, BlockSummary prev_summary, NetworkParams params, long tx_count)
  {
    BlockSummary.Builder bs = BlockSummary.newBuilder();

    BigInteger target = BlockchainUtil.targetBytesToBigInteger(header.getTarget());

    BigInteger slice = BigInteger.valueOf(1024L);

    // So a block at max target is 'slice' number of work units
    // A block at half the target (harder) is twice the number of slices.

    BigInteger work_in_block = params.getMaxTarget().multiply(slice).divide(target);

    // SIP2 - work is multipled by 4^activated_field.  That way, a higher field
    // takes precedence.
    BigInteger field_multipler = BigInteger.ONE.shiftLeft(prev_summary.getActivatedField() * 2);
    work_in_block = work_in_block.multiply(field_multipler);

    BigInteger prev_work_sum = BlockchainUtil.readInteger(prev_summary.getWorkSum());

    bs.setTotalTransactions( prev_summary.getTotalTransactions() + tx_count );

    BigInteger worksum = prev_work_sum.add(work_in_block);

    bs.setWorkSum(worksum.toString());

    long weight = params.getAvgWeight();
    long decay = 1000L - weight;
    BigInteger decay_bi = BigInteger.valueOf(decay);
    BigInteger weight_bi = BigInteger.valueOf(weight);

    long block_time;
    long prev_block_time;
    BigInteger prev_target_avg;

    if (prev_summary.getHeader().getTimestamp() == 0)
    { // first block, just pick a time
      block_time = params.getBlockTimeTarget();
      prev_block_time = params.getBlockTimeTarget();
      prev_target_avg = params.getMaxTarget();
    }
    else
    {
      block_time = header.getTimestamp() - prev_summary.getHeader().getTimestamp();
      prev_block_time = prev_summary.getBlocktimeAverageMs();
      prev_target_avg = BlockchainUtil.readInteger(prev_summary.getTargetAverage());
    }
    int field = prev_summary.getActivatedField();
    bs.setActivatedField( field );

    SnowFieldInfo next_field = params.getSnowFieldInfo(field + 1);
    if (next_field != null)
    {
      /*System.out.println(String.format("Field %d Target %f, activation %f", field+1,
        PowUtil.getDiffForTarget(prev_target_avg),
        PowUtil.getDiffForTarget(next_field.getActivationTarget())));*/
      if (prev_target_avg.compareTo(next_field.getActivationTarget()) <= 0)
      {
        bs.setActivatedField( field + 1 );
      }
    }

    bs.setBlocktimeAverageMs(  (prev_block_time * decay + block_time * weight) / 1000L );

    bs.setTargetAverage(
      prev_target_avg.multiply(decay_bi)
        .add(target.multiply(weight_bi))
        .divide(BigInteger.valueOf(1000L))
        .toString());

    bs.setHeader(header);

    return bs.build();

  }


  
}
