package snowblossom.miner;

import snowblossom.mining.proto.WorkUnit;
import snowblossom.lib.*;
import java.nio.ByteBuffer;
import java.util.Random;
import java.security.MessageDigest;


public class PartialWork implements Comparable<PartialWork>
{
  public WorkUnit wu;
  byte[] nonce;
  int passes_done;
  byte[] context;

  long next_word_idx;

  byte[] word_buff = new byte[SnowMerkle.HASH_LEN];
  ByteBuffer word_bb = ByteBuffer.wrap(word_buff);


  public PartialWork(WorkUnit wu, Random rnd, MessageDigest md, long total_words)
  {
    nonce = new byte[Globals.NONCE_LENGTH];
    rnd.nextBytes(nonce);
    wu.getHeader().getNonce().copyTo(nonce, 0);
    context = PowUtil.hashHeaderBits(wu.getHeader(), nonce, md);
    
    next_word_idx = PowUtil.getNextSnowFieldIndex(context, total_words, md);

  }

  // more passes done is first
  public int compareTo(PartialWork o)
  {
    if (passes_done > o.passes_done) return -1;
    if (passes_done < o.passes_done) return 1;
    return 0;
  }

  public long getNextWordIdx()
  {
    return next_word_idx;
  }

  public void doPass(FieldSource fs, MessageDigest md, long total_words)
    throws java.io.IOException
  {
    word_bb.clear();
    fs.readWord(next_word_idx, word_bb);
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
