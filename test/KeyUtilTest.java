package snowblossom;

import org.junit.Assert;
import org.junit.Test;
import org.junit.BeforeClass;

import java.security.KeyPair;
import com.google.protobuf.ByteString;
import java.security.PublicKey;
import java.security.PrivateKey;

import snowblossom.proto.WalletKeyPair;
import snowblossom.proto.SigSpec;

import java.util.Random;

import java.util.logging.Logger;
import java.util.logging.Level;


public class KeyUtilTest
{
  private static final Logger logger = Logger.getLogger("KeyUtilTest");

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

  @Test
  public void testCompressedWallet()
    throws Exception
  {
    WalletKeyPair wkp = KeyUtil.generateWalletCompressedECKey();

    testKeyPair(wkp);
  }

  @Test
  public void testAllowedCurves()
    throws Exception
  {
    for(String curve : SignatureUtil.ALLOWED_ECDSA_CURVES)
    {
      WalletKeyPair wkp = KeyUtil.generateWalletECKey(curve);

      testKeyPair(wkp);
    }
  }
 
  @Test
  public void testRSA()
    throws Exception
  {
    for(int i=512; i<=4*1024; i*=2)
    {
      logger.info("Generating key size: " + i);

      WalletKeyPair wkp = KeyUtil.generateWalletRSAKey(i);

      logger.info(KeyUtil.decomposeASN1Encoded(wkp.getPublicKey()));

      logger.info("Testing key size: " + i);
      testKeyPair(wkp);
    }
  }
 
  @Test
  public void testDSA()
    throws Exception
  {
      logger.info("Generating DSA");

      WalletKeyPair wkp = KeyUtil.generateWalletDSAKey();

      logger.info(KeyUtil.decomposeASN1Encoded(wkp.getPublicKey()));

      testKeyPair(wkp);
  }



  private void testKeyPair(WalletKeyPair wkp)
    throws Exception
  {
    Random rnd = new Random();
    byte[] b = new byte[Globals.BLOCKCHAIN_HASH_LEN];
    rnd.nextBytes(b);

    ChainHash hash = new ChainHash(b);

    ByteString sig = SignatureUtil.sign(wkp, hash);
    SigSpec sig_spec = SigSpec.newBuilder()
      .setSignatureType(wkp.getSignatureType())
      .setPublicKey(wkp.getPublicKey())
      .build();


    Assert.assertTrue(SignatureUtil.checkSignature(sig_spec, hash.getBytes(), sig));


  }


}
