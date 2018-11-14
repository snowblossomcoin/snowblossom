
package snowblossom.miner;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.List;
import java.util.TreeSet;

import java.nio.ByteBuffer;
import snowblossom.lib.Globals;
import snowblossom.lib.SnowMerkle;
import java.util.logging.Logger;

public class FieldSourceComposit extends FieldSource
{
  private List<FieldSource> sources;

  public FieldSourceComposit(List<FieldSource> sources)
  {
    this.sources = sources;
    TreeSet<Integer> total=new TreeSet<>();

    for(FieldSource fs : sources)
    {
      total.addAll(fs.getHoldingSet());
    }

    holding_set = ImmutableSet.copyOf(total);
  }


  public void bulkRead(long word_index, ByteBuffer bb) throws java.io.IOException
  {
    int chunk = (int)(word_index / words_per_chunk);
    for(FieldSource fs : sources)
    {
      
      if (fs.hasChunk(chunk))
      {
        if (bb.remaining() == 16)
        {
          fs.readWord(word_index, bb);
        }
        else
        {
          fs.bulkRead(word_index, bb);
        }
        return;
      }
    }
    throw new RuntimeException("Unable to find field for chunk: " + chunk);

  }


}
