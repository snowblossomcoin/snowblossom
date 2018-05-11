package snowblossom;

import org.junit.Assert;
import org.junit.Test;
import org.junit.BeforeClass;

import java.security.KeyPair;
import com.google.protobuf.ByteString;
import java.security.PublicKey;
import java.security.PrivateKey;

public class KeyUtilTest
{
  @BeforeClass
  public static void loadProvider()
  {
    Globals.addCryptoProvider();
  }


  @Test
  public void testCompressKeyEncoding()
		throws Exception
  {
    KeyPair pair = KeyUtil.generateECCompressedKey();

		ByteString encoded = KeyUtil.getCompressedPublicKeyEncoding(pair.getPublic());

		Assert.assertEquals(33, encoded.size());

		PublicKey k = KeyUtil.convertCompressedECDSA(encoded);

		Assert.assertEquals(k, pair.getPublic());
  }

  @Test
  public void testCompressPrivateKeyEncoding()
		throws Exception
  {
    KeyPair pair = KeyUtil.generateECCompressedKey();

		ByteString encoded = ByteString.copyFrom(pair.getPrivate().getEncoded());
    System.out.println("Format: " + pair.getPrivate().getFormat());

    PrivateKey k = KeyUtil.decodePrivateKey(encoded, "ECDSA");

		Assert.assertEquals(k, pair.getPrivate());
  }


}
