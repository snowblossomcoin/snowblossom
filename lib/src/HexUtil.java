package snowblossom.lib;

import com.google.protobuf.ByteString;
import org.apache.commons.codec.binary.Hex;

public class HexUtil
{
  public static ByteString hexStringToBytes(String s)
  {
    try
    { 
      Hex h = new Hex();
      return ByteString.copyFrom(h.decode(s.getBytes()));
    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  public static String getHexString(byte[] data)
  {
    Hex h = new Hex();
    return new String(h.encodeHex(data));
  }

  public static String getHexString(ByteString bs)
  {
    return getHexString(bs.toByteArray());
  }

  public static String getSafeString(ByteString data)
  {
    return getSafeString(new String(data.toByteArray()));  
  }
  
  public static String getSafeString(String in)
  {
    StringBuilder sb = new StringBuilder();

    for(int i = 0; i<in.length(); i++)
    {
      char z = in.charAt(i);
      char u = '.'; // character to print

      if (z == ' ') u = z;
      if (z == '-') u = z;
      if (z == '_') u = z;
      if (z == '.') u = z;
      if (z == ':') u = z;
      if (Character.isDigit(z)) u = z;
      if (Character.isLetter(z)) u = z;
      sb.append(u);
    }


    return sb.toString();
  }
 
}
