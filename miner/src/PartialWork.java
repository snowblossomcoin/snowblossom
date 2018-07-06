package snowblossom.miner;

import snowblossom.mining.proto.WorkUnit;
import snowblossom.lib.*;
import java.nio.ByteBuffer;
import java.util.Random;
import java.security.MessageDigest;

import org.junit.Assert;

import com.google.common.annotations.VisibleForTesting;


public class PartialWork implements Comparable<PartialWork>
{
  public WorkUnit wu;
  byte[] nonce;
  int passes_done;
  byte[] context;

  long next_word_idx;

  byte[] word_buff = new byte[SnowMerkle.HASH_LEN];
  ByteBuffer word_bb = ByteBuffer.wrap(word_buff);

  @VisibleForTesting
  public PartialWork(int pass_no)
  {
    passes_done = pass_no;
  }

  public PartialWork(WorkUnit wu, Random rnd, MessageDigest md, long total_words)
  {
    this.wu = wu;
    nonce = new byte[Globals.NONCE_LENGTH];
    rnd.nextBytes(nonce);
    wu.getHeader().getNonce().copyTo(nonce, 0);
    context = PowUtil.hashHeaderBits(wu.getHeader(), nonce, md);
    
    next_word_idx = PowUtil.getNextSnowFieldIndex(context, total_words, md);

  }

  // more passes done is first
  public int compareTo(PartialWork o)
  {
    Assert.assertNotNull(o);
    if (passes_done > o.passes_done) return -1;
    if (passes_done < o.passes_done) return 1;

    //if (sort < o.sort) return -1;
    //if (sort > o.sort) return 1;
    return 0;
  }
  public boolean equals(Object o)
  {
    System.out.println("Equals called");
    return super.equals(o);
  }

  public long getNextWordIdx()
  {
    return next_word_idx;
  }

  public void doPass(byte[] word_buff, MessageDigest md, long total_words)
    throws java.io.IOException
  {
    //System.out.println("Pass: " + passes_done);
    Assert.assertTrue(next_word_idx >= 0);
    context = PowUtil.getNextContext(context, word_buff, md);

    passes_done++;
    if (passes_done < Globals.POW_LOOK_PASSES)
    {
      next_word_idx = PowUtil.getNextSnowFieldIndex(context, total_words, md);
    }
    else
    {
      next_word_idx = -1;
    }
  }


}
