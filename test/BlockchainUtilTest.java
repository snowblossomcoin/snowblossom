package snowblossom;

import com.google.protobuf.ByteString;
import org.junit.Assert;
import org.junit.Test;
import snowblossomlib.BlockchainUtil;

import java.math.BigInteger;
import java.util.Random;


public class BlockchainUtilTest
{
  @Test
  public void testTargetConversion()
  {
    Random rnd = new Random();

    byte[] b=new byte[32]; 
    for(int i=0; i<50000; i++)
    {
      rnd.nextBytes(b);

      b[0]=0;

      ByteString bs_in = ByteString.copyFrom(b);
      BigInteger bi = snowblossomlib.BlockchainUtil.targetBytesToBigInteger(bs_in);

      ByteString bs_out = BlockchainUtil.targetBigIntegerToBytes(bi);

      Assert.assertEquals(bs_in, bs_out);

    }
  }
  

}

