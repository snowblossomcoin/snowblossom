package snowblossom.miner;

import java.security.MessageDigest;

import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import java.nio.ByteBuffer;

import com.google.protobuf.ByteString;

import org.junit.Assert;

import snowblossom.lib.*;
import snowblossom.proto.*;

import java.util.LinkedList;
import java.util.List;


public class ProofGen
{
  public static SnowPowProof getProof(FieldSource field_source, FieldSource deck_source, long word_index, long total_words) throws java.io.IOException
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
      getInnerProof(field_source, deck_source, md, partners, word_index, 0, total_words);

      SnowPowProof.Builder builder = SnowPowProof.newBuilder();
      builder.setWordIdx(word_index);
      builder.addAllMerkleComponent(partners);

      return builder.build();
    }
  }

  /**
   * If the target is not in specified subtree, return hash of subtree
   * If the target is in the specified subtree, return null and add hash partner from
   * opposite subtree to partners
   */
  private static ByteString getInnerProof(
    FieldSource field_source, FieldSource deck_source,
    MessageDigest md, List<ByteString> partners, 
    long target_word_index, long start, long end) throws java.io.IOException
  {
    boolean inside = false;
    if ((start <= target_word_index) && (target_word_index < end))
    {
      inside = true;
    }

    long dist = end - start;
    if (!inside)
    {
      if (deck_source.getDeckFiles().containsKey(dist))
      {
        long deck_pos = SnowMerkle.HASH_LEN_LONG * (start / dist);
        byte[] buff = new byte[SnowMerkle.HASH_LEN];
        ByteBuffer bb = ByteBuffer.wrap(buff);

        ChannelUtil.readFully(deck_source.getDeckFiles().get(dist), bb, deck_pos);

        return ByteString.copyFrom(buff);
      }

      if (dist == 1)
      {
        byte[] buff = new byte[SnowMerkle.HASH_LEN];
        ByteBuffer bb = ByteBuffer.wrap(buff);
        field_source.bulkRead(start, bb);

        return ByteString.copyFrom(buff);

      }
      long mid = (start + end) / 2;

      ByteString left = getInnerProof(field_source, deck_source, md, partners, target_word_index, start, mid);
      ByteString right = getInnerProof(field_source, deck_source, md, partners, target_word_index, mid, end);

      md.update(left.toByteArray());
      md.update(right.toByteArray());

      byte[] hash = md.digest();

      return ByteString.copyFrom(hash);

    }
    else
    { // we are inside

      if (dist == 1)
      {
        byte[] buff = new byte[SnowMerkle.HASH_LEN];
        ByteBuffer bb = ByteBuffer.wrap(buff);
        field_source.bulkRead(start, bb);

        partners.add(ByteString.copyFrom(buff));
        return null;
      }

      long mid = (start + end) / 2;

      ByteString left = getInnerProof(field_source, deck_source, md, partners, target_word_index, start, mid);
      ByteString right = getInnerProof(field_source, deck_source, md, partners, target_word_index, mid, end);

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
