package lib.test;

import com.google.protobuf.ByteString;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import snowblossom.proto.SigSpec;
import snowblossom.proto.WalletKeyPair;
import snowblossom.lib.ChainHash;
import snowblossom.lib.Globals;
import snowblossom.lib.KeyUtil;
import snowblossom.lib.SignatureUtil;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Logger;


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
  public void testStandardWallet()
    throws Exception
  {
    WalletKeyPair wkp = KeyUtil.generateWalletStandardECKey();

    testKeyPair(wkp, "standard");
  }

  @Test
  public void testAllowedCurves()
    throws Exception
  {
    for(String curve : SignatureUtil.ALLOWED_ECDSA_CURVES)
    {
      WalletKeyPair wkp = KeyUtil.generateWalletECKey(curve);

      testKeyPair(wkp, curve);
    }
  }
  @Test
  public void testAllowedCurvesByName()
    throws Exception
  {
    ArrayList<String> curves = new ArrayList<>();
    curves.add("secp256k1");
    curves.add("secp384r1");
    curves.add("secp521r1");
    curves.add("sect571k1");
    curves.add("sect571r1");
    for(String curve : curves)
    {
      WalletKeyPair wkp = KeyUtil.generateWalletECKey(curve);

      testKeyPair(wkp, curve);
    }
  }
 
  @Test
  public void testRSA()
    throws Exception
  {

    for(int i=512; i<=2*1024; i*=2)
    {
      logger.info("Generating key size: " + i);

      WalletKeyPair wkp = KeyUtil.generateWalletRSAKey(i);

      logger.info(KeyUtil.decomposeASN1Encoded(wkp.getPublicKey()));

      logger.info("Testing key size: " + i);
      testKeyPair(wkp, "RSA " + i);
    }
  }

  @Test
  public void testDSA()
    throws Exception
  {
      logger.info("Generating DSA");

      WalletKeyPair wkp = KeyUtil.generateWalletDSAKey();

      logger.info(KeyUtil.decomposeASN1Encoded(wkp.getPublicKey()));

      testKeyPair(wkp, "DSA");
  }

  @Test
  public void testDSTU4145()
    throws Exception
  {
    for(int i=0; i<=9; i++)
    {
      logger.info("Testing DSTU key size: " + i);
      WalletKeyPair wkp = KeyUtil.generateWalletDSTU4145Key(i);

      logger.info(KeyUtil.decomposeASN1Encoded(wkp.getPublicKey()));

      testKeyPair(wkp, "DSTU " + i);
    }
  } 

  private void testKeyPair(WalletKeyPair wkp, String name)
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

    logger.info(String.format("Key report %s Pub size: %d, sig %d", name, wkp.getPublicKey().size(), sig.size()));

    Assert.assertTrue(SignatureUtil.checkSignature(sig_spec, hash.getBytes(), sig));


  }


}
