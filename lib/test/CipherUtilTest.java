package lib.test;

import com.google.protobuf.ByteString;
import java.util.Random;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assert;
import snowblossom.lib.CipherUtil;
import snowblossom.lib.Globals;
import snowblossom.lib.SignatureUtil;
import snowblossom.lib.KeyUtil;
import snowblossom.proto.SigSpec;
import snowblossom.proto.WalletKeyPair;
import snowblossom.util.proto.SymmetricKey;

public class CipherUtilTest
{
  @BeforeClass
  public static void loadProvider()
  {
    Globals.addCryptoProvider();
  }

  @Test
  public void emptyTest()
  {
    

  }

  /*@Test
  public void testDSA()
    throws Exception
  {
    WalletKeyPair wkp = KeyUtil.generateWalletDSAKey();
    testKeys(wkp);
  }*/


  /*@Test
  public void testRSA()
    throws Exception
  {
    WalletKeyPair wkp = KeyUtil.generateWalletRSAKey(1024);
    testKeys(wkp);
  }*/

  @Test
  public void testECCompressed()
    throws Exception
  {
    WalletKeyPair wkp = KeyUtil.generateWalletStandardECKey();
    testKeys(wkp);
  }

  @Test
  public void testECCurves()
    throws Exception
  {
    for(String curve : SignatureUtil.ALLOWED_ECDSA_CURVES)
    {
      
      WalletKeyPair wkp = KeyUtil.generateWalletECKey(curve);
      testKeys(wkp);
    }

  }

  @Test
  public void testSymmetricCipher() throws Exception
  {
    SymmetricKey key = CipherUtil.generageSymmetricKey();

    Random rnd = new Random();
    for(int i=0; i<100; i++)
    {
      byte[] b = new byte[rnd.nextInt(100000)];
      if (i ==0) b = new byte[0];

      rnd.nextBytes(b);

      ByteString input = ByteString.copyFrom(b);

      ByteString output = CipherUtil.encryptSymmetric(key, input);
      ByteString output2 = CipherUtil.encryptSymmetric(key, input);

      Assert.assertTrue(output.size() >= input.size());

      Assert.assertFalse(output.equals(output2));

      ByteString dec = CipherUtil.decryptSymmetric(key, output);
      Assert.assertEquals(input, dec);


    }
    

  }




  private void testKeys(WalletKeyPair wkp)
    throws Exception
  {
    SigSpec sig_spec = SigSpec.newBuilder()
      .setSignatureType(wkp.getSignatureType())
      .setPublicKey(wkp.getPublicKey())
      .build();

    Random rnd = new Random();

    for(int i=0; i<100; i++)
    {

      byte[] b = new byte[rnd.nextInt(100000)];
      if (i ==0) b = new byte[0];

      rnd.nextBytes(b);

      ByteString input = ByteString.copyFrom(b);

      ByteString output = CipherUtil.encrypt(sig_spec, input);
      ByteString output2 = CipherUtil.encrypt(sig_spec, input);

      Assert.assertTrue(output.size() >= input.size());

      Assert.assertFalse(output.equals(output2));

      ByteString dec = CipherUtil.decrypt(wkp, output);
      Assert.assertEquals(input, dec);

    }

  }

}
