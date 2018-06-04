package snowblossom.lib.trie;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import org.apache.commons.codec.binary.Hex;

import java.security.MessageDigest;
import java.util.List;

public class HashUtils
{
  public static ByteString hashOfEmpty()
  {
    return hashConcat(ImmutableList.of());
  }

	public static ByteString hashConcat(List<ByteString> words)
	{
    try
    {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
			for(ByteString bs : words)
			{
      	md.update(bs.toByteArray());
			}
      return ByteString.copyFrom(md.digest());

    }
    catch (java.security.NoSuchAlgorithmException e)
    {
            throw new RuntimeException(e);
    }
	}

	public static String getHexString(byte[] data)
	{
    return Hex.encodeHexString(data);
	}

	public static String getHexString(ByteString bs)
	{
		return getHexString(bs.toByteArray());
	}

}
