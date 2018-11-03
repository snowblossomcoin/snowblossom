
package snowblossom.miner;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.Map;
import java.nio.channels.FileChannel;

import java.nio.ByteBuffer;
import snowblossom.lib.Globals;
import snowblossom.lib.SnowMerkle;
import java.util.logging.Logger;
import java.text.DecimalFormat;

import duckutil.MultiAtomicLong;

public abstract class FieldSource
{
  protected ImmutableSet<Integer> holding_set;
  protected static final Logger logger = Logger.getLogger("snowblossom.miner");

  public final long words_per_chunk = Globals.MINE_CHUNK_SIZE / SnowMerkle.HASH_LEN_LONG;

  protected MultiAtomicLong read_counter = new MultiAtomicLong();


  /**
   * Read the 16-byte word at word_index into the byte buffer
   */
  public void readWord(long word_index, ByteBuffer bb) throws java.io.IOException
  {
    bulkRead(word_index, bb);
    read_counter.add(1L);
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
  public Map<Long, FileChannel> getDeckFiles()
  {
    return null;
  }

  public String getRateString(double elapsed_sec)
  {
    double reads = read_counter.sumAndReset();
    double read_rate = reads / elapsed_sec;
    double data_rate = read_rate * 4096; //4k blocks on every damn thing
    double data_rate_mb = data_rate / 1048576.0;

    DecimalFormat df = new DecimalFormat("0.0");
    return String.format("read_ops/s: %s read_bw: %s MB/s", df.format(read_rate), df.format(data_rate_mb));

  }

}
