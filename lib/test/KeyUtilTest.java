package lib.test;

import com.google.protobuf.ByteString;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import snowblossom.lib.ChainHash;
import snowblossom.lib.Globals;
import snowblossom.lib.HexUtil;
import snowblossom.lib.KeyUtil;
import snowblossom.lib.SignatureUtil;
import snowblossom.proto.SigSpec;
import snowblossom.proto.WalletKeyPair;

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
    for(int i=0; i<100; i++)
    {
      KeyPair pair = KeyUtil.generateECCompressedKey();

      ByteString encoded = KeyUtil.getCompressedPublicKeyEncoding(pair.getPublic());

      Assert.assertEquals(33, encoded.size());
      //System.out.println("Comp Byte: " + encoded.byteAt(0));

      PublicKey k = KeyUtil.convertCompressedECDSA(encoded);

      Assert.assertEquals(k, pair.getPublic());
    }
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

      testKeyPair(wkp, "DSTU " + i);
    }
  }

  @Test
  public void testSphincsWallet()
    throws Exception
  {
    WalletKeyPair wkp = KeyUtil.generateWalletSphincsPlusKey();
    testKeyPair(wkp, "sphincs");
  }

  @Test
  public void testDilithiumWallet()
    throws Exception
  {
    WalletKeyPair wkp = KeyUtil.generateWalletDilithiumKey();
    testKeyPair(wkp, "dilithium");
  }

  private void testKeyPair(WalletKeyPair wkp, String name)
    throws Exception
  {
    Random rnd = new Random();
    byte[] b = new byte[Globals.BLOCKCHAIN_HASH_LEN];
    for(int i=0; i<8; i++)
    {
      rnd.nextBytes(b);

      ChainHash hash = new ChainHash(b);

      long t1,t2;

      t1 = System.nanoTime();
      ByteString sig = SignatureUtil.sign(wkp, hash);
      t2 = System.nanoTime() - t1;
      double sign_time = t2;

      SigSpec sig_spec = SigSpec.newBuilder()
        .setSignatureType(wkp.getSignatureType())
        .setPublicKey(wkp.getPublicKey())
        .build();


      t1 = System.nanoTime();
      Assert.assertTrue(SignatureUtil.checkSignature(sig_spec, hash.getBytes(), sig));
      t2 = System.nanoTime() - t1;
      double check_time = t2;

      DecimalFormat df = new DecimalFormat("0.000000");


      logger.info(String.format("Key report %s Pub size: %d, sig %d, sign time: %s ms, check time: %s ms",
        name, wkp.getPublicKey().size(), sig.size(),
        df.format(sign_time/1e6), df.format(check_time/1e6)
        ));
      logger.info("Key report: " + HexUtil.getHexString( sig));
    }


  }


}
