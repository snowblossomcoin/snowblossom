package lib.src;

import com.google.protobuf.ByteString;
import org.junit.Assert;

import java.math.BigInteger;
import java.nio.ByteBuffer;

public class BlockchainUtil
{
  public static BigInteger targetBytesToBigInteger(ByteString bytes)
  {
    Assert.assertEquals(Globals.TARGET_LENGTH, bytes.size());

    return new BigInteger(1, bytes.toByteArray());

  }

  public static ByteString targetBigIntegerToBytes(BigInteger target)
  {
    ByteBuffer bb = ByteBuffer.allocate(Globals.TARGET_LENGTH);

    byte[] data = target.toByteArray();
    int zeros = Globals.TARGET_LENGTH - data.length;

    for (int i = 0; i < zeros; i++)
    {
      byte z = 0;
      bb.put(z);
    }
    bb.put(data);

    return ByteString.copyFrom(bb.array());
  }

  public static BigInteger getTargetForDiff(int n)
  {
    return BigInteger.ONE.shiftLeft(256 - n);
  }

  public static BigInteger readInteger(String s)
  {
    if (s.length() == 0) return BigInteger.ZERO;

    return new BigInteger(s);
  }
}
