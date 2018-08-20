package snowblossom.lib;

import com.google.protobuf.ByteString;
import org.junit.Assert;
import snowblossom.lib.trie.ByteStringComparator;
import snowblossom.lib.trie.HashUtils;

/**
 * Represents a basic hash that could be anything on
 * the blockchain.  A transaction ID, a block ID, a merkle root, etc.
 */
public class ChainHash implements Comparable<ChainHash>
{
  private ByteString bytes;

  public static final ChainHash ZERO_HASH = new ChainHash(new byte[Globals.BLOCKCHAIN_HASH_LEN]);

  public ChainHash(ByteString bs)
  {
    Assert.assertEquals(Globals.BLOCKCHAIN_HASH_LEN, bs.size());
    this.bytes = bs;
  }
  public ChainHash(byte[] b)
  {
    this(ByteString.copyFrom(b));
  }

  public ChainHash(String str)
  {
    Assert.assertEquals(Globals.BLOCKCHAIN_HASH_LEN*2, str.length());
    this.bytes = HexUtil.hexStringToBytes(str);
  }

  @Override
  public String toString()
  {
    return HashUtils.getHexString(bytes);
  }

  @Override
  public int hashCode()
  {
    return bytes.hashCode();
  }

  public byte[] toByteArray()
  {
    return bytes.toByteArray();
  }

  public ByteString getBytes()
  {
    return bytes;
  }

  public int compareTo(ChainHash o)
  {
    return ByteStringComparator.compareStatic(this.getBytes(), o.getBytes());
  }

  @Override
  public boolean equals(Object o)
  {
    if (o instanceof ChainHash)
    {
       ChainHash c = (ChainHash)o;
       return (ByteStringComparator.compareStatic(this.getBytes(), c.getBytes())==0);
    }
    if (o instanceof ByteString)
    {
      ByteString b = (ByteString)o;

       return (ByteStringComparator.compareStatic(this.getBytes(), b)==0);
    }
    return super.equals(o);

  }

}
