package snowblossom;

import java.security.MessageDigest;
import org.junit.Assert;

import java.nio.ByteBuffer;

import snowblossom.proto.BlockHeader;
import snowblossom.proto.BlockSummary;
import snowblossom.trie.ByteStringComparator;

import com.google.protobuf.ByteString;

import snowblossom.trie.HashUtils;

import java.util.logging.Logger;

import java.text.DecimalFormat;
import java.math.BigInteger;

public class PowUtil
{
  private static final Logger logger = Logger.getLogger("snowblossom.blockchain");

  public static byte[] hashHeaderBits(BlockHeader header, byte[] nonce)
  {
    MessageDigest md = DigestUtil.getMD();
    return hashHeaderBits(header, nonce, md);

  }
  public static byte[] hashHeaderBits(BlockHeader header, byte[] nonce, MessageDigest md)
  {

    byte[] int_data = new byte[3*4 + 1*8];
    ByteBuffer bb = ByteBuffer.wrap(int_data);

    bb.putInt(header.getVersion());
    bb.putInt(header.getBlockHeight());
    bb.putLong(header.getTimestamp());
    bb.putInt(header.getSnowField());

    Assert.assertEquals(0, bb.remaining());
    
    md.update(nonce);
    md.update(int_data);
    md.update(header.getPrevBlockHash().toByteArray());
    md.update(header.getMerkleRootHash().toByteArray());
    md.update(header.getUtxoRootHash().toByteArray());
    md.update(header.getTarget().toByteArray());

    return md.digest();
  }

  public static long getNextSnowFieldIndex(byte[] context, long word_count)
  {
    MessageDigest md = DigestUtil.getMD();
    return getNextSnowFieldIndex(context, word_count, md);

  }
  public static long getNextSnowFieldIndex(byte[] context, long word_count, MessageDigest md)
  {
    md.update(context);
    byte[] hash = md.digest();

    byte[] longdata = new byte[8];

    for(int i=1; i<8; i++)
    {
      longdata[i] = hash[i];
    }
    ByteBuffer bb = ByteBuffer.wrap(longdata);
    long v = bb.getLong();

    return v % word_count;
  }

  public static byte[] getNextContext(byte[] prev_context, byte[] found_data)
  {
    MessageDigest md = DigestUtil.getMD();
    return getNextContext(prev_context, found_data, md);

  }
  public static byte[] getNextContext(byte[] prev_context, byte[] found_data, MessageDigest md)
  {
    md.update(prev_context);
    md.update(found_data);
    return md.digest();
  }

  public static boolean lessThanTarget(byte[] found_hash, ByteString target)
  {
    ByteString found = ByteString.copyFrom(found_hash,0, Globals.TARGET_LENGTH);

    return (ByteStringComparator.compareStatic(found, target) < 0);

  }

  public static BigInteger calcNextTarget(BlockSummary prev_summary, NetworkParams params, long clock_time)
  {
    if (prev_summary.getHeader().getTimestamp() == 0) return params.getMaxTarget();


    long weight = params.getAvgWeight();
    long decay = 1000L - weight;

    long delta_t = clock_time - prev_summary.getHeader().getTimestamp();

    long prev_block_time = prev_summary.getBlocktimeAverageMs();

    long averaged_delta_t = (prev_block_time * decay + delta_t * weight) / 1000L ;

    long target_delta_t = params.getBlockTimeTarget();


    // Scale > 0 means that the time is too long -> decrease difficulty
    // Scale < 0 means the time is too short -> increase difficulty
    long scale = averaged_delta_t * 1000 / target_delta_t - 1000;

    // Take whatever we thing the correction factor is and
    // half that
    scale = scale / 2;


    // Max change is 50% per block
    scale = Math.min(scale, 500);
    scale = Math.max(scale, -500);

    // Larger target -> less work

    BigInteger prev_target_average = new BigInteger(prev_summary.getTargetAverage());
    BigInteger scale_bi = BigInteger.valueOf(scale);
    BigInteger thousand = BigInteger.valueOf(1000L);


    BigInteger new_target = prev_target_average.add( 
      prev_target_average.multiply(scale_bi).divide(thousand) );
    //long new_target = prev_summary.getTargetAverage() + prev_summary.getTargetAverage() * scale / 1000;

    logger.info(String.format("Delta_t: %d (%d) scale: %d",averaged_delta_t, target_delta_t, scale));

    ByteBuffer bb = ByteBuffer.allocate(8);

    new_target = new_target.min(params.getMaxTarget());

    ByteString new_target_display = BlockchainUtil.targetBigIntegerToBytes(new_target).substring(0,16);

    double diff = getDiffForTarget(new_target);
    double avg_diff = getDiffForTarget( prev_target_average );
    DecimalFormat df = new DecimalFormat("0.000");
    logger.info(String.format("New target: %s, %s, (avg %s)", 
      HashUtils.getHexString(new_target_display), 
      df.format(diff),
      df.format(avg_diff)));

    return new_target;

  }

  /**
   * Not for use in the chain, only for display purposes. lossy.
   */
  public static double getDiffForTarget(BigInteger target)
  {
    double t = target.doubleValue();
    return 256.0 - Math.log(t) / Math.log(2);
  }

  public static long getBlockReward(NetworkParams params, int block_height)
  {
    // half every 2 years
    long blocks_per_day = 86400 / (params.getBlockTimeTarget() / 1000);
    int blocks_per_two_years = (int)blocks_per_day * 365;

    long reward = 50000000;
    int n = block_height;
    while(n >= blocks_per_two_years)
    {
      n -= blocks_per_two_years;
      reward = reward / 2;
    }
    return reward;
    
  }


}
