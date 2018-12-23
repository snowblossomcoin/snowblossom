package snowblossom.miner;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import org.junit.Assert;
import snowblossom.lib.*;
import snowblossom.proto.SnowPowProof;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import java.util.Set;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.junit.Assert;
import duckutil.Config;

public class FieldSourceFile extends FieldSource
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  private final RandomAccessFile[] snow_file;
  private final FileChannel[] snow_file_channel;

  private final ImmutableMap<Long, FileChannel> deck_files;

  private final long total_words;
  private final int total_chunk;

  private final String name;

  public FieldSourceFile(Config conf, int layer, NetworkParams params, int field_number, File path) throws java.io.IOException
  {
    name = path.toString() + "#" + field_number;

    SnowFieldInfo field_info = params.getSnowFieldInfo(field_number);

    total_chunk = (int)(field_info.getLength() / Globals.MINE_CHUNK_SIZE); 
    total_words = field_info.getLength() / SnowMerkle.HASH_LEN_LONG;
    String base = params.getFieldSeeds().get(field_number);

    snow_file = new RandomAccessFile[total_chunk];
    snow_file_channel = new FileChannel[total_chunk];

    Set<Integer> chunks = new TreeSet<Integer>();

		int start =0;
		int end = total_chunk;

		if (conf.isSet("layer_" + layer + "_range"))
		{

			List<String> range_lst = conf.getList("layer_" + layer + "_range");
			if (range_lst.size() != 2)
			{
				throw new RuntimeException("Expected range of two numbers.  Example: 0,17");
			}
			start = Integer.parseInt(range_lst.get(0));
			end = Math.min(Integer.parseInt(range_lst.get(1)), end);

			Assert.assertTrue(end >= start);
			Assert.assertTrue(start >= 0);
		}

    for(int i=start; i< end; i++)
    {
      String hex = Integer.toString(i, 16);
      while(hex.length() < 4) hex = "0" + hex;
      File f = new File(new File(path, base), String.format("%s.snow.%s", base, hex));
      if (f.exists())
      {
        snow_file[i] = new RandomAccessFile(f, "r");
        snow_file_channel[i] = snow_file[i].getChannel();
        chunks.add(i);
      }
    }

    holding_set = ImmutableSet.copyOf(chunks);
    
    int deck_count = SnowMerkle.getNumberOfDecks(total_words);
    long h = 1;

    TreeMap<Long, FileChannel> deck_map = new TreeMap<>();
    int missing_deck = 0;

    for (int i = 0; i < deck_count; i++)
    {
      h = h * SnowMerkle.DECK_ENTIRES;

      char letter = (char) ('a' + i);
      File f = new File(new File(path, base), base + ".deck." + letter);
      if (f.exists())
      {

        RandomAccessFile deck_file = new RandomAccessFile(f, "r");
        FileChannel deck_channel = deck_file.getChannel();

        long expected_len = field_info.getLength() / h;
        if (deck_file.length() != expected_len)
        {
          throw new java.io.IOException("Unexpected length on " + base + ".deck." + letter);
        }

        deck_map.put(h, deck_channel);
      }
      else
      {
        missing_deck++;
      }
    }
    if (missing_deck == 0)
    {
      deck_files = ImmutableMap.copyOf(deck_map);
    }
    else
    {
      deck_files = null;
    }

  }

  @Override
  public boolean hasDeckFiles()
  {
    return (deck_files != null);
  }

  @Override
  public Map<Long, FileChannel> getDeckFiles()
  {
    return deck_files;
  }


  @Override
  public void bulkRead(long word_index, ByteBuffer bb) throws java.io.IOException
  {
    
    int chunk =  (int)(word_index / words_per_chunk);
    long word_offset = word_index % words_per_chunk;

    Assert.assertTrue(hasChunk(chunk));

    long read_offset = word_offset * SnowMerkle.HASH_LEN_LONG;
    ChannelUtil.readFully( snow_file_channel[chunk], bb, read_offset);
  }

  @Override
  public String toString(){return "FieldSource-" + name; }

}
