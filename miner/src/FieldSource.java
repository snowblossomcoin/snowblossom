
package snowblossom.miner;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

import java.nio.ByteBuffer;
import snowblossom.lib.Globals;
import snowblossom.lib.SnowMerkle;
import java.util.logging.Logger;

public abstract class FieldSource
{
  protected ImmutableSet<Integer> holding_set;
  protected static final Logger logger = Logger.getLogger("snowblossom.miner");

  protected final long words_per_chunk = Globals.MINE_CHUNK_SIZE / SnowMerkle.HASH_LEN_LONG;

  /**
   * Read the 16-byte word at word_index into the byte buffer
   */
  public void readWord(long word_index, ByteBuffer bb) throws java.io.IOException
  {
    bulkRead(word_index, bb);
  }

  public abstract void bulkRead(long word_index, ByteBuffer bb) throws java.io.IOException;

  public ImmutableSet<Integer> getHoldingSet() {return holding_set; }
  public boolean hasChunk(int n)
  {
    return holding_set.contains(n);
  }

  public boolean skipQueueOnRehit()
  {
    return false;
  }

  public boolean hasDeckFiles()
  {
    return false;
  }

}
