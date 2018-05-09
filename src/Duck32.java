package snowblossom;

import com.google.protobuf.ByteString;
import java.security.MessageDigest;
import java.math.BigInteger;
import java.util.TreeMap;

import org.junit.Assert;
import com.google.common.collect.ImmutableMap;


public class Duck32
{

  /** The Bech32 character set for encoding. */
  public static final String CHARSET =     "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
  public static final String BIG_INT_SET = "0123456789abcdefghijklmnopqrstuv";

  public static final int HASH_BYTES = 5;

  public static final ImmutableMap<Character, Character> TO_BECH32_MAP = makeToBech32Map();
  public static final ImmutableMap<Character, Character> TO_BASE32_MAP = makeToBase32Map();


  private static ImmutableMap<Character, Character> makeToBech32Map()
  {
    TreeMap<Character, Character> m = new TreeMap<>();
    for(int i=0; i<32; i++)
    {
      m.put( BIG_INT_SET.charAt(i), CHARSET.charAt(i) );
    }
    return ImmutableMap.copyOf(m);
  }
  private static ImmutableMap<Character, Character> makeToBase32Map()
  {
    TreeMap<Character, Character> m = new TreeMap<>();
    for(int i=0; i<32; i++)
    {
      m.put( CHARSET.charAt(i), BIG_INT_SET.charAt(i));
    }
    return ImmutableMap.copyOf(m);
  }


  public static String encode(String label, ByteString data)
  {
    Assert.assertEquals(Globals.ADDRESS_SPEC_HASH_LEN, data.size());

    ByteString whole = applyChecksum(label, data);


    StringBuilder sb = new StringBuilder();
    sb.append(label);
    sb.append(":");
    sb.append(convertBytesToBase32(whole));
    return sb.toString();

  }

  public static ByteString decode(String expected_label, String encoding)
    throws ValidationException
  {
    
    int colon = encoding.indexOf(':');
    String data_str;
    if (colon > 0)
    {
      String found_label = encoding.substring(0, colon);
      data_str = encoding.substring(colon+1);
      if (!expected_label.equals(found_label))
      {
        throw new ValidationException(String.format("Expected label %s, found %s", expected_label, found_label));
      }
    }
    else
    {
      data_str = encoding;
    }
    ByteString fulldata = convertBase32ToBytes(data_str);

    ByteString data = fulldata.substring(0, Globals.ADDRESS_SPEC_HASH_LEN);
    ByteString checksum = fulldata.substring(Globals.ADDRESS_SPEC_HASH_LEN);
    validateChecksum(expected_label, data, checksum);
    
    return data;
  }

  private static ByteString applyChecksum(String label, ByteString data)
  {
    MessageDigest md = DigestUtil.getMD();

    md.update(label.getBytes());
    md.update(data.toByteArray());
    byte[] hash = md.digest();

    ByteString checksum = ByteString.copyFrom(hash, 0, HASH_BYTES);

    return data.concat(checksum);
  }
  private static void validateChecksum(String label, ByteString data, ByteString checksum)
    throws ValidationException
  {
    ByteString expected = applyChecksum(label, data);
    ByteString have = data.concat(checksum);

    if (!expected.equals(have)) throw new ValidationException("Checksum mismatch");

  }

  private static String convertBase32ToBech32(String input)
  {
    StringBuilder sb = new StringBuilder(input.length());
    for(int i=0; i<input.length(); i++)
    {
      sb.append(TO_BECH32_MAP.get( input.charAt(i) ) );
    }
    return sb.toString();
  }
  private static String convertBech32ToBase32(String input)
  {
    StringBuilder sb = new StringBuilder(input.length());
    for(int i=0; i<input.length(); i++)
    {
      sb.append(TO_BASE32_MAP.get( input.charAt(i) ) );
    }
    return sb.toString();
  }


  private static String convertBytesToBase32(ByteString input)
  {
    BigInteger big=new BigInteger(1, input.toByteArray());
    String base32=big.toString(32);
    return convertBase32ToBech32(base32);
  }

  private static ByteString convertBase32ToBytes(String encoding)
  {
    BigInteger big = new BigInteger(convertBech32ToBase32(encoding), 32);
    byte[] data = big.toByteArray();

    int data_size=HASH_BYTES + Globals.ADDRESS_SPEC_HASH_LEN;


    // Helpful biginteger might throw an extra zero byte on the front to show positive sign
    int start = data.length - data_size;
    if (start >= 0)
    {
      return ByteString.copyFrom(data, start, Globals.ADDRESS_SPEC_HASH_LEN + HASH_BYTES);
    }
    else
    {
      byte[] zeros = new byte[data_size];
      int needed_zeros = data_size - data.length;
      return ByteString.copyFrom(zeros, 0, needed_zeros).concat(ByteString.copyFrom(data));
    }
  }

}
