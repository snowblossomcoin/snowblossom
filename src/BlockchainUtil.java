package snowblossom;

import java.nio.ByteBuffer;
import com.google.protobuf.ByteString;

import org.junit.Assert;

public class BlockchainUtil
{
  public static long targetBytesToLong(ByteString bytes)
  {
    Assert.assertEquals(8, bytes.size());

    ByteBuffer bb = ByteBuffer.wrap(bytes.toByteArray());
    return bb.getLong();
  }

  public static ByteString targetLongToBytes(long target)
  {
    ByteBuffer bb = ByteBuffer.allocate(8);
    bb.putLong(target);

    return ByteString.copyFrom(bb.array());
  }

}
