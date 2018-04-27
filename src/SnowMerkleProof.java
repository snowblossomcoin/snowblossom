package snowblossom;
import snowblossom.proto.SnowPowProof;

import com.google.protobuf.ByteString;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;

import java.io.RandomAccessFile;
import java.util.List;
import java.util.LinkedList;
import java.util.TreeMap;
import java.io.File;
import java.security.MessageDigest;

import org.junit.Assert;
import com.google.common.collect.ImmutableMap;
import snowblossom.trie.HashUtils;

public class SnowMerkleProof
{
  public static boolean checkProof(SnowPowProof proof, ByteString expected_merkle_root, long snow_field_size)
  {
    long target_index = proof.getWordIdx();
    long word_count = snow_field_size / SnowMerkle.HASH_LEN_LONG;
    if (target_index < 0) return false;
    if (target_index >= word_count) return false;

    MessageDigest md;
    try
    {
      md = MessageDigest.getInstance(Globals.SNOW_MERKLE_HASH_ALGO);
    }
    catch(java.security.NoSuchAlgorithmException e)
    {
      throw new RuntimeException( e );
    }

    LinkedList<ByteString> stack = new LinkedList<>();
    stack.addAll(proof.getMerkleComponentList());
    
    ByteString current_hash = stack.poll();
    if (current_hash == null) return false;

    long start = target_index;
    long end = target_index;
    long dist = 1;

    // To visualize this, take the recursive getInnerProof() below
    // and do it backwards
    while ((stack.size() > 0) && (end <= word_count))
    {
      dist *= 2;
      start = start - (start % dist);
      end = start + dist;
      long mid = (start + end) / 2;

      ByteString left = null;
      ByteString right = null;
      if (target_index < mid)
      {
        left = current_hash;
        right = stack.poll();
      }
      else
      {
        left = stack.poll();
        right = current_hash;
      }

      md.update(left.toByteArray());
      md.update(right.toByteArray());

      byte[] hash = md.digest();
      current_hash = ByteString.copyFrom(hash);

    }

    // We expect to end with our sublist being the entire file
    // so we check to avoid someone passing off an intermediate node
    // as a merkle tree member
    if (start != 0) return false;
    if (end != word_count) return false;

    if (current_hash.equals(expected_merkle_root)) return true;

    return false;
  }

  private final RandomAccessFile snow_file;
  private final FileChannel snow_file_channel;

  private final ImmutableMap<Long, FileChannel> deck_files;
  private final long total_words;

  /**
   * Only needed by miners
   */
  public SnowMerkleProof(File path, String base)
    throws java.io.IOException
  {
    snow_file = new RandomAccessFile(new File(path, base +".snow"), "r");
    snow_file_channel = snow_file.getChannel();
    
    total_words = snow_file.length() / SnowMerkle.HASH_LEN_LONG;

    int deck_count = SnowMerkle.getNumberOfDecks(total_words);
    long h = 1;

		TreeMap<Long, FileChannel> deck_map = new TreeMap<>();

		for(int i = 0; i<deck_count; i++)
		{ 
			h = h * SnowMerkle.DECK_ENTIRES;

			char letter = (char) ('a' + i);
			RandomAccessFile deck_file = new RandomAccessFile(new File(path, base +".deck." + letter), "r");
			FileChannel deck_channel = deck_file.getChannel();

			deck_map.put(h, deck_channel);
		}
		deck_files = ImmutableMap.copyOf(deck_map);

  }

  public SnowPowProof getProof(long word_index)
    throws java.io.IOException
  {
    LinkedList<ByteString> partners = new LinkedList<ByteString>();

    MessageDigest md;
    try
    {
      md = MessageDigest.getInstance(Globals.SNOW_MERKLE_HASH_ALGO);
    }
    catch(java.security.NoSuchAlgorithmException e)
    {
      throw new RuntimeException( e );
    }
    getInnerProof(md, partners, word_index, 0, total_words);

    SnowPowProof.Builder builder = SnowPowProof.newBuilder();
    builder.setWordIdx(word_index);
    builder.addAllMerkleComponent(partners);

    return builder.build();
  }

  public long getTotalWords()
  {
    return total_words;
  }

  public byte[] readWord(long word_index)
    throws java.io.IOException
  {
    long word_pos = word_index * SnowMerkle.HASH_LEN_LONG;
    byte[] buff = new byte[SnowMerkle.HASH_LEN];
    ByteBuffer bb = ByteBuffer.wrap(buff);
    ChannelUtil.readFully(snow_file_channel, bb, word_pos);

    return buff;
  }

  /**
   * If the target is not in specified subtree, return hash of subtree
   * If the target is in the specified subtree, return null and add hash partner from
   * opposite subtree to partners
   */
  private ByteString getInnerProof(MessageDigest md, List<ByteString> partners, long target_word_index, long start, long end)
    throws java.io.IOException
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
      long mid = (start + end ) / 2;
      
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
