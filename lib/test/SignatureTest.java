package lib.test;

import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.asn1.ASN1Encodable;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import snowblossom.lib.Globals;
import snowblossom.lib.KeyUtil;

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
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
      java.security.spec.RSAKeyGenParameterSpec.F0);
    
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

    testAlgo("ECDSA", spec, "SHA256withECDSA", null, 10240);
    testAlgo("ECDSA", spec, "ECDSA", null, 24);
  }

  @Test
  public void testEC384()
    throws Exception
  {
    java.security.spec.ECGenParameterSpec spec = new java.security.spec.ECGenParameterSpec("secp384r1");

    testAlgo("ECDSA", spec, "SHA256withECDSA", null, 10240);
    testAlgo("ECDSA", spec, "ECDSA", null, 24);
  }
  @Test
  public void testEC521()
    throws Exception
  {
    java.security.spec.ECGenParameterSpec spec = new java.security.spec.ECGenParameterSpec("secp521r1");

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
    java.security.spec.ECGenParameterSpec spec = new java.security.spec.ECGenParameterSpec("1.2.804.2.1.1.1.1.3.1.1.2.0");

    testAlgo("DSTU4145", spec, "GOST3411WITHDSTU4145", null, 10240);
    testAlgo("DSTU4145", spec, "DSTU4145", null, 24000); //whatever the hell this is, seems to handle large data size
    testAlgo("DSTU4145", spec, "DSTU4145", null, 24);
  }

  @Test
  public void testCompressedEcPrefix() throws Exception
  {
    java.security.spec.ECGenParameterSpec spec = new java.security.spec.ECGenParameterSpec("secp256k1");
    KeyPairGenerator key_gen = KeyPairGenerator.getInstance("ECDSA");
    key_gen.initialize(spec);

    KeyPair pair = key_gen.genKeyPair();
    PublicKey pub = pair.getPublic();
    PrivateKey priv = pair.getPrivate();

    if (pub instanceof org.spongycastle.jcajce.provider.asymmetric.ec.BCECPublicKey)
    {
      org.spongycastle.jcajce.provider.asymmetric.ec.BCECPublicKey pk = (org.spongycastle.jcajce.provider.asymmetric.ec.BCECPublicKey) pub;
      pk.setPointFormat("COMPRESSED");
    }

    if (pub instanceof org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey)
    {
      org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey pk = (org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey) pub;
      pk.setPointFormat("COMPRESSED");
    }


    byte[] encoded = pub.getEncoded();

    byte[] prefix = new byte[encoded.length -33];
    for(int i=0; i<prefix.length; i++)
    {
      prefix[i] = encoded[i];
    }
    System.out.println("secp256k1 prefix: " + Hex.encodeHexString(prefix));

    Assert.assertEquals(Hex.encodeHexString(KeyUtil.EC_SECP256K1_PREFIX.toByteArray()), Hex.encodeHexString(prefix));

  }
  @Test
  public void testCompressedEcGames() throws Exception
  {
    java.security.spec.ECGenParameterSpec spec = new java.security.spec.ECGenParameterSpec("secp256k1");
    KeyPairGenerator key_gen = KeyPairGenerator.getInstance("ECDSA");
    key_gen.initialize(spec);

    KeyPair pair = key_gen.genKeyPair();
    PublicKey pub = pair.getPublic();
    PrivateKey priv = pair.getPrivate();

    if (pub instanceof org.spongycastle.jcajce.provider.asymmetric.ec.BCECPublicKey)
    {
      org.spongycastle.jcajce.provider.asymmetric.ec.BCECPublicKey pk = (org.spongycastle.jcajce.provider.asymmetric.ec.BCECPublicKey) pub;
      pk.setPointFormat("COMPRESSED");
    }

    if (pub instanceof org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey)
    {
      org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey pk = (org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey) pub;
      pk.setPointFormat("COMPRESSED");
    }

    System.out.println("Class: " + pub.getClass());

    byte[] encoded = pub.getEncoded();

    System.out.println("Compressed size: " + encoded.length);

    X509EncodedKeySpec spec_dec = new X509EncodedKeySpec(encoded);

    KeyFactory fact = KeyFactory.getInstance("ECDSA");
    PublicKey decoded = fact.generatePublic(spec_dec);
    System.out.println("Keyclass: " + decoded.getClass());

    Assert.assertEquals(pub, decoded);

    byte[] recoded = decoded.getEncoded();


    org.bouncycastle.asn1.ASN1StreamParser parser = new org.bouncycastle.asn1.ASN1StreamParser(encoded);

    org.bouncycastle.asn1.DERSequenceParser der_p = (org.bouncycastle.asn1.DERSequenceParser) parser.readObject();
    org.bouncycastle.asn1.DERSequenceParser der_p2 = (org.bouncycastle.asn1.DERSequenceParser) der_p.readObject();

    ASN1Encodable encodable = der_p2.readObject();
    System.out.println(encodable.getClass());
    System.out.println(encodable);

    encodable = der_p2.readObject();
    System.out.println(encodable.getClass());
    System.out.println(encodable);

    //encodable = der_p2.readObject();
    //System.out.println(encodable.getClass());
    //System.out.println(encodable);

  }

  private void testAlgo(
      String gen_algo, AlgorithmParameterSpec gen_spec,
      String sign_algo, AlgorithmParameterSpec sign_spec,
      int data_size
      )
    throws Exception
  {
    System.out.println("------------------------" + gen_algo + "/" + sign_algo);
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

    System.out.println("" + algo + " public key: " + Hex.encodeHexString(encoded));

    X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);

    KeyFactory fact = KeyFactory.getInstance(algo);
    PublicKey decoded = fact.generatePublic(spec);

    byte[] recoded = decoded.getEncoded();

    Assert.assertArrayEquals(encoded, recoded);

    System.out.println(KeyUtil.decomposeASN1Encoded(recoded));

  }

}
