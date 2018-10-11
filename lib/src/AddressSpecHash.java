package snowblossom.lib;

import com.google.protobuf.ByteString;
import org.junit.Assert;
import snowblossom.lib.trie.ByteStringComparator;
import snowblossom.lib.trie.HashUtils;

/**
 * Represents a hash of an address
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

  public AddressSpecHash(String address, NetworkParams params)
    throws ValidationException
  {
    AddressSpecHash o = AddressUtil.getHashForAddress( params.getAddressPrefix(), address);
    bytes = o.getBytes();

    if (bytes.size() != Globals.ADDRESS_SPEC_HASH_LEN)
    {
      throw new ValidationException("Address length wrong");
    }
  }

  public AddressSpecHash(String str)
  {
    Assert.assertEquals(Globals.ADDRESS_SPEC_HASH_LEN*2, str.length());
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

  public String toAddressString(NetworkParams params)
  {
    return AddressUtil.getAddressString(params.getAddressPrefix(), this);
  }

}
