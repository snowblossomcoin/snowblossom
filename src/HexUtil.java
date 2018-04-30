package snowblossom;

import org.apache.commons.codec.binary.Hex;

import com.google.protobuf.ByteString;

public class HexUtil
{
  public static ByteString stringToHex(String s)
  {
    try
    { 
      return ByteString.copyFrom(Hex.decodeHex(s));
    }
    catch(Exception e)
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
