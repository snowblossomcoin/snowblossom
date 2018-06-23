package snowblossom.miner;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import org.junit.Assert;
import snowblossom.lib.ChannelUtil;
import snowblossom.lib.Globals;
import snowblossom.lib.SnowMerkle;
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
import java.util.logging.Logger;


public class SnowMerkleProof
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  private final RandomAccessFile snow_file;
  private final FileChannel snow_file_channel;

  private final ImmutableMap<Long, FileChannel> deck_files;
  private final long total_words;
  private final boolean memcache;

  private long bytes_to_precache = 0;
  private byte[][] mem_buff;
  public static final int MEM_BLOCK = 1024 * 1024;
  private int minDepthToDisk;

  private final ThreadLocal<SnowMerkleProof> diskProof;

  public SnowMerkleProof(File path, String base) throws java.io.IOException
  {
    this(path, base, false, 0, 6);
  }

  /**
   * Only needed by miners
   */
  public SnowMerkleProof(File path, String base, boolean memcache, long bytesToPreCache, int minDepthToDisk) throws java.io.IOException
  {
    this.memcache = memcache;
    this.minDepthToDisk = minDepthToDisk;
    snow_file = new RandomAccessFile(new File(path, base + ".snow"), "r");
    snow_file_channel = snow_file.getChannel();

    total_words = snow_file.length() / SnowMerkle.HASH_LEN_LONG;

    int deck_count = SnowMerkle.getNumberOfDecks(total_words);
    long h = 1;

    TreeMap<Long, FileChannel> deck_map = new TreeMap<>();

    for (int i = 0; i < deck_count; i++)
    {
      h = h * SnowMerkle.DECK_ENTIRES;

      char letter = (char) ('a' + i);
      RandomAccessFile deck_file = new RandomAccessFile(new File(path, base + ".deck." + letter), "r");
      FileChannel deck_channel = deck_file.getChannel();

      long expected_len = snow_file.length() / h;
      if (deck_file.length() != expected_len)
      {
        throw new java.io.IOException("Unexpected length on " + base + ".deck." + letter);
      }

      deck_map.put(h, deck_channel);
    }
    deck_files = ImmutableMap.copyOf(deck_map);

    if (memcache)
    {
      mem_buff = new byte[(int) (snow_file.length() / MEM_BLOCK)][];
    }
    if (bytesToPreCache > 0)
    {
      bytes_to_precache = bytesToPreCache - (bytesToPreCache % MEM_BLOCK);
      diskProof = new ThreadLocal<SnowMerkleProof>(){
        @Override
        protected SnowMerkleProof initialValue()
        {
          try
          {
            return new SnowMerkleProof(path, base);
          }
          catch (Exception e)
          {
            throw new RuntimeException(e);
          }
        }
      };
    }
    else
    {
      diskProof = null;
    }
  }

  public long getLength() throws java.io.IOException
  {
    return snow_file.length();
  }

  public SnowPowProof getProof(long word_index) throws java.io.IOException
  {
    try (TimeRecordAuto tra = TimeRecord.openAuto("SnowMerkleProof.getProof"))
    {
      LinkedList<ByteString> partners = new LinkedList<ByteString>();

      MessageDigest md;
      try
      {
        md = MessageDigest.getInstance(Globals.SNOW_MERKLE_HASH_ALGO);
      }
      catch (java.security.NoSuchAlgorithmException e)
      {
        throw new RuntimeException(e);
      }
      getInnerProof(md, partners, word_index, 0, total_words);

      SnowPowProof.Builder builder = SnowPowProof.newBuilder();
      builder.setWordIdx(word_index);
      builder.addAllMerkleComponent(partners);

      return builder.build();
    }
  }

  public long getTotalWords()
  {
    return total_words;
  }

  public void readChunk(long offset, ByteBuffer bb) throws java.io.IOException
  {
    ChannelUtil.readFully(snow_file_channel, bb, offset);

  }

  /**
   * Reads a 16 byte section of the snowfield
   * @param word_index which 16 byte section to read
   * @param bb a buffer to put the 16 bytes into
   * @param currentDepth a number 0-5 which represents which read into the snowfield it is for the POW algorithm.
   * @return true if the word was read, false if it wasn't (aka, too shallow, not precached).
   * @throws java.io.IOException
   */
  public boolean readWord(long word_index, ByteBuffer bb, int currentDepth) throws java.io.IOException
  {
    if (bytes_to_precache > 0)
    {
      synchronized (this)
      {
        if (bytes_to_precache > 0)
        {
          mem_buff = new byte[(int) (bytes_to_precache / MEM_BLOCK)][];
          int blocksToPrecache = (int) (bytes_to_precache / MEM_BLOCK);
          for (int i = 0; i < blocksToPrecache; i++)
          {
            if (i % 1000 == 0)
            {
              int percentage = (int) ((100L * i) / blocksToPrecache);
              logger.info("pre-caching snowfield: loaded " + (i / 1000) + " gb of " + (blocksToPrecache / 1000) + " (" + percentage + "%)");
            }
            byte[] block_data = new byte[MEM_BLOCK];
            long file_offset = i * (long) MEM_BLOCK;
            ChannelUtil.readFully(snow_file_channel, ByteBuffer.wrap(block_data), file_offset);
            mem_buff[i] = block_data;
          }
        }
        bytes_to_precache = -1;
      }
    }


    long word_pos = word_index * SnowMerkle.HASH_LEN_LONG;
    int mem_block_index = (int) (word_pos / MEM_BLOCK);
    if (mem_buff != null && mem_block_index < mem_buff.length)
    {
      int off_in_block = (int) (word_pos % MEM_BLOCK);
      if (mem_buff[mem_block_index] == null)
      {
        Assert.assertEquals(0, bytes_to_precache);
        //try (TimeRecordAuto tra2 = TimeRecord.openAuto("SnowMerkleProof.readBlock"))
        {
          byte[] block_data = new byte[MEM_BLOCK];
          long file_offset = (long) mem_block_index * (long) MEM_BLOCK;
          ChannelUtil.readFully(snow_file_channel, ByteBuffer.wrap(block_data), file_offset);
          mem_buff[mem_block_index] = block_data;
        }
      }
      bb.put(mem_buff[mem_block_index], off_in_block, SnowMerkle.HASH_LEN);
      return true;
    }

    if (bytes_to_precache == -1 && minDepthToDisk > currentDepth) return false;
    if (diskProof != null)
    {
      diskProof.get().readWord(word_index, bb, Globals.POW_LOOK_PASSES);
      return true;
    }

    ChannelUtil.readFully(snow_file_channel, bb, word_pos);
    return true;
  }

  /**
   * If the target is not in specified subtree, return hash of subtree
   * If the target is in the specified subtree, return null and add hash partner from
   * opposite subtree to partners
   */
  private ByteString getInnerProof(MessageDigest md, List<ByteString> partners, long target_word_index, long start, long end) throws java.io.IOException
  {
    boolean inside = false;
    if ((start <= target_word_index) && (target_word_index < end))
    {
      inside = true;
    }

    long dist = end - start;
    if (!inside)
    {
      if (deck_files.containsKey(dist))
      {
        long deck_pos = SnowMerkle.HASH_LEN_LONG * (start / dist);
        byte[] buff = new byte[SnowMerkle.HASH_LEN];
        ByteBuffer bb = ByteBuffer.wrap(buff);

        ChannelUtil.readFully(deck_files.get(dist), bb, deck_pos);

        return ByteString.copyFrom(buff);
      }

      if (dist == 1)
      {
        long word_pos = start * SnowMerkle.HASH_LEN_LONG;
        byte[] buff = new byte[SnowMerkle.HASH_LEN];
        ByteBuffer bb = ByteBuffer.wrap(buff);
        ChannelUtil.readFully(snow_file_channel, bb, word_pos);

        return ByteString.copyFrom(buff);

      }
      long mid = (start + end) / 2;

      ByteString left = getInnerProof(md, partners, target_word_index, start, mid);
      ByteString right = getInnerProof(md, partners, target_word_index, mid, end);

      md.update(left.toByteArray());
      md.update(right.toByteArray());

      byte[] hash = md.digest();

      return ByteString.copyFrom(hash);

    }
    else
    { // we are inside

      if (dist == 1)
      {
        long word_pos = start * SnowMerkle.HASH_LEN_LONG;
        byte[] buff = new byte[SnowMerkle.HASH_LEN];
        ByteBuffer bb = ByteBuffer.wrap(buff);
        ChannelUtil.readFully(snow_file_channel, bb, word_pos);

        partners.add(ByteString.copyFrom(buff));
        return null;
      }

      long mid = (start + end) / 2;

      ByteString left = getInnerProof(md, partners, target_word_index, start, mid);
      ByteString right = getInnerProof(md, partners, target_word_index, mid, end);

      if (target_word_index < mid)
      {
        partners.add(right);
        Assert.assertNull(left);
      }
      else
      {
        partners.add(left);
        Assert.assertNull(right);
      }

      return null;

    }

  }

}
