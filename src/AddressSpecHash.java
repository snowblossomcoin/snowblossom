package snowblossom;

import org.junit.Assert;

import snowblossom.trie.HashUtils;
import snowblossom.trie.ByteStringComparator;
import com.google.protobuf.ByteString;

/**
 * Represents a basic hash that could be anything on
 * the blockchain.  A transaction ID, a block ID, a merkle root, etc.
 */
public class AddressSpecHash implements Comparable<AddressSpecHash>
{
  private ByteString bytes;

  public AddressSpecHash(ByteString bs)
  {
    Assert.assertEquals(Globals.ADDRESS_SPEC_HASH_LEN, bs.size());
    this.bytes = bs;
  }
  public AddressSpecHash(byte[] b)
  {
    this(ByteString.copyFrom(b));
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

  public ByteString getBytes()
  {
    return bytes;
  }

  public int compareTo(AddressSpecHash o)
  {
    return ByteStringComparator.compareStatic(this.getBytes(), o.getBytes());
  }

  @Override
  public boolean equals(Object o)
  {
    if (o instanceof AddressSpecHash)
    {
       AddressSpecHash c = (AddressSpecHash)o;
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
