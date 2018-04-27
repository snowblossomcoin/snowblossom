package snowblossom;

import org.junit.Assert;
import org.junit.Test;

import org.junit.BeforeClass;

import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.Signature;
import java.security.KeyFactory;

import java.security.spec.X509EncodedKeySpec;

import java.util.Random;

/** Might not be testing any actual snowblossom code, just making sure I understand 
 * how signatures work.
 */
public class SignatureTest
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

  @Test
  public void testRSA()
    throws Exception
  {
    java.security.spec.RSAKeyGenParameterSpec spec = new java.security.spec.RSAKeyGenParameterSpec(2048, 
      java.security.spec.RSAKeyGenParameterSpec.F4);
    
    // RSA should just work with any key size someone feels like making so whatever

    testAlgo("RSA", spec, "SHA256withRSA", null, 10240);
    testAlgo("RSA", spec, "RSA", null, 24);
  }
  @Test
  public void testDSA()
    throws Exception
  {
    java.security.spec.DSAGenParameterSpec spec = new java.security.spec.DSAGenParameterSpec(3072,256);

    //not sure why this doesn't work or what spec it is using
    spec = null;

    // DSA should just work with any key size someone feels like making so whatever

    testAlgo("DSA", spec, "SHA256withDSA", null, 10240);
    testAlgo("DSA", spec, "DSA", null, 24);
  }

  @Test
  public void testEC()
    throws Exception
  {
    java.security.spec.ECGenParameterSpec spec = new java.security.spec.ECGenParameterSpec("secp256k1");

    // Using secp256k1 as the key gen but not for the signing is probably wrong
    // this is probably using the wrong curve

    testAlgo("ECDSA", spec, "SHA256withECDSA", null, 10240);
    testAlgo("ECDSA", spec, "ECDSA", null, 24);
  }


  // Who the hell knows what this is?
  @Test
  public void testDSTU4145()
    throws Exception
  {
    // There are oids ending with 0 through 9 which seem to map to 
    // DSTU 4145-163 to DSTU 4145-431 which is not very helpful
    java.security.spec.ECGenParameterSpec spec = new java.security.spec.ECGenParameterSpec("1.2.804.2.1.1.1.1.3.1.1.2.7");

    testAlgo("DSTU4145", spec, "GOST3411WITHDSTU4145", null, 10240);
    testAlgo("DSTU4145", spec, "DSTU4145", null, 24000); //whatever the hell this is, seems to handle large data size
    testAlgo("DSTU4145", spec, "DSTU4145", null, 24);
  }

  private void testAlgo(
      String gen_algo, AlgorithmParameterSpec gen_spec,
      String sign_algo, AlgorithmParameterSpec sign_spec,
      int data_size
      )
    throws Exception
  {
    KeyPairGenerator key_gen = KeyPairGenerator.getInstance(gen_algo);

    if (gen_spec != null) key_gen.initialize(gen_spec);

    KeyPair pair = key_gen.genKeyPair();
    PublicKey pub = pair.getPublic();
    PrivateKey priv = pair.getPrivate();
    
    System.out.println(String.format("%s pubkey %s", gen_algo, pub.toString()));
    testEncodeKey(gen_algo, pub);

    Signature sig_sign = Signature.getInstance(sign_algo);

    if (sign_spec != null) sig_sign.setParameter(sign_spec);
    sig_sign.initSign(priv);

    Random rnd = new Random();
    byte[] data = new byte[data_size];
    rnd.nextBytes(data);

    sig_sign.update(data);
    byte[] sig_data = sig_sign.sign();
    System.out.println(String.format("%s signature size: %d",sign_algo, sig_data.length));

    Signature sig_verify = Signature.getInstance(sign_algo);

    if (sign_spec != null) sig_verify.setParameter(sign_spec);
    sig_verify.initVerify(pub);
    sig_verify.update(data);

    Assert.assertTrue(sig_verify.verify(sig_data));

  }
  private void testEncodeKey(String algo, PublicKey pub)
    throws Exception
  {
    byte[] encoded = pub.getEncoded();
    System.out.println(String.format("%s encode %s size: %d", algo, pub.getFormat(), encoded.length));

    X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);

    KeyFactory fact = KeyFactory.getInstance(algo);
    PublicKey decoded = fact.generatePublic(spec);

    byte[] recoded = decoded.getEncoded();

    Assert.assertArrayEquals(encoded, recoded);

  }

}
