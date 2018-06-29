
package snowblossom.miner;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.Collection;
import java.util.List;

import java.nio.ByteBuffer;
import snowblossom.lib.Globals;
import snowblossom.lib.SnowMerkle;

public class FieldSourceMem extends FieldSource
{
  private byte chunks[][];

  public FieldSourceMem(Collection<Integer> to_have, List<FieldSource> sources)
    throws java.io.IOException
  {
    int max = 0;
    for(int x : to_have) max = Math.max(max, x);
    chunks = new byte[max+1][];

    holding_set = ImmutableSet.copyOf(to_have);

    for(int x : to_have)
    {
      chunks[x] = new byte[(int)Globals.MINE_CHUNK_SIZE];
      ByteBuffer bb = ByteBuffer.wrap(chunks[x]);

      boolean found=false;
      for(FieldSource fs : sources)
      {
        if (!found)
        {
          if (fs.hasChunk(x))
          {
            long xl = x;
            logger.info(String.format("Reading chunk %d into memory from %s", x, fs.toString()));
            fs.bulkRead( words_per_chunk * xl, bb );
            found=true;
          }
        }
      }
      if (!found)
      {
        throw new RuntimeException(String.format("Unable to load chunk %d into memory.  Not in sources.", x));
      }
    }
  }

  @Override
  public void bulkRead(long word_index, ByteBuffer bb) throws java.io.IOException
  {
    int chunk =  (int)(word_index / words_per_chunk);
    long word_offset = word_index % words_per_chunk;
    int read_offset = (int)(word_offset * SnowMerkle.HASH_LEN_LONG);

    bb.put(chunks[chunk], read_offset, bb.remaining());


  }

  @Override
  public String toString()
  {
    return "FieldSourceMem{" + holding_set.size() + "}";
  }
  
  @Override
  public boolean skipQueueOnRehit()
  {
    return true;
  }

}
