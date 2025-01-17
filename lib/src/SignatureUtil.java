package snowblossom.lib;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.pqc.jcajce.provider.dilithium.BCDilithiumPublicKey;
import org.bouncycastle.pqc.jcajce.provider.falcon.BCFalconPublicKey;
import org.bouncycastle.pqc.jcajce.provider.sphincsplus.BCSPHINCSPlusPublicKey;
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.FalconParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.SPHINCSPlusParameterSpec;
import snowblossom.proto.SigSpec;
import snowblossom.proto.WalletKeyPair;

public class SignatureUtil
{
  /**
   * The public key sent on the network shall be just "03" or "02" plus the 32 bytes of X
   * The curve is assumed to be secp256k1.  Just like bitcoin.
   */
  public static final int SIG_TYPE_ECDSA_COMPRESSED=1; //secp256k1 only

  /**
   * Everything else the public key shall be a x509 encoded public key.
   * For EC keys, the x509 encoded data includes the specific curve to use.
   */

  public static final int SIG_TYPE_ECDSA=2;
  public static final int SIG_TYPE_DSA=3;
  public static final int SIG_TYPE_RSA=4;
  public static final int SIG_TYPE_DSTU4145=5;
  public static final int SIG_TYPE_SPHINCSPLUS=6;
  public static final int SIG_TYPE_DILITHIUM=7;
  public static final int SIG_TYPE_FALCON=8;

  public static PublicKey decodePublicKey(SigSpec sig_spec)
    throws ValidationException
  {
    int sig_type = sig_spec.getSignatureType();
    ByteString encoded = sig_spec.getPublicKey();

    if (sig_type == SIG_TYPE_ECDSA_COMPRESSED)
    {
      return KeyUtil.convertCompressedECDSA(encoded);
    }
    else
    {
      String algo = "";
      //ArrayList<String> oidList = KeyUtil.extractObjectIdentifiers(encoded);

      if (sig_type == SIG_TYPE_ECDSA)
      {
        algo="ECDSA";

        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(encoded.toByteArray()));
        AlgorithmIdentifier algid = spki.getAlgorithm();
        //ASN1ObjectIdentifier oid_o = (ASN1ObjectIdentifier) algid.getParameters();
        //String oid = oid_o.getId();
        String oid = algid.getParameters().toString().replace("[","").replace("]","").trim();

        if (!ALLOWED_ECDSA_CURVES.contains(oid))
          throw new ValidationException(
            String.format("OID %s not on allowed list for %s", oid.toString(), algo));
      }
      if (sig_type == SIG_TYPE_DSA)
      {
        algo="DSA";
      }
      if (sig_type == SIG_TYPE_RSA)
      {
        algo="RSA";
      }
      if (sig_type == SIG_TYPE_DSTU4145)
      {
        algo="DSTU4145";

        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(encoded.toByteArray()));
        AlgorithmIdentifier algid = spki.getAlgorithm();
        String oid = algid.getParameters().toString().replace("[","").replace("]","").trim();

        if (!ALLOWED_DSTU4145_CURVES.contains(oid))
          throw new ValidationException(
            String.format("OID %s not on allowed list for %s", oid.toString(), algo));
      }
      if (sig_type == SIG_TYPE_SPHINCSPLUS)
      {
        algo="SPHINCSPLUS";
        PublicKey pub_key = KeyUtil.decodeKey(encoded, algo, sig_type);
        BCSPHINCSPlusPublicKey s_key = (BCSPHINCSPlusPublicKey) pub_key;

        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(encoded.toByteArray()));
        AlgorithmIdentifier algid = spki.getAlgorithm();
        System.out.println("Sphincs OID: " + algid.toASN1Primitive());
        System.out.println("Sphincs OID: " + algid.getAlgorithm());

        String oid = algid.getAlgorithm().toString();
        if (!oid.equals("1.3.6.1.4.1.22554.2.5.5"))
        {
          throw new ValidationException("Wrong OID - got " + oid);
        }

        if (! s_key.getParameterSpec().equals(SPHINCSPlusParameterSpec.haraka_128s))
        {
          throw new ValidationException("Only haraka_128s allows for SphincsPlus keys");
        }
      }
      if (sig_type == SIG_TYPE_DILITHIUM)
      {
        algo="DILITHIUM";
        PublicKey pub_key = KeyUtil.decodeKey(encoded, algo, sig_type);
        BCDilithiumPublicKey d_key = (BCDilithiumPublicKey) pub_key;

        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(encoded.toByteArray()));
        AlgorithmIdentifier algid = spki.getAlgorithm();
        System.out.println("Dilithium OID: " + algid.toASN1Primitive());
        System.out.println("Dilithium OID: " + algid.getAlgorithm());
        String oid = algid.getAlgorithm().toString();
        if (!oid.equals("1.3.6.1.4.1.2.267.12.8.7"))
        {
          throw new ValidationException("Wrong OID - got " + oid);
        }


        if (! d_key.getParameterSpec().equals(DilithiumParameterSpec.dilithium5))
        {
          throw new ValidationException("Only dilithium5 allowed for DILITHIUM keys");
        }
      }
      if (sig_type == SIG_TYPE_FALCON)
      {
        algo="FALCON";
        PublicKey pub_key = KeyUtil.decodeKey(encoded, algo, sig_type);
        BCFalconPublicKey d_key = (BCFalconPublicKey) pub_key;

        SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(ASN1Sequence.getInstance(encoded.toByteArray()));
        AlgorithmIdentifier algid = spki.getAlgorithm();
        String oid = algid.getAlgorithm().toString();
        if (!oid.equals("1.3.9999.3.6"))
        {
          throw new ValidationException("Wrong OID - got " + oid);
        }

        if (! d_key.getParameterSpec().equals(FalconParameterSpec.falcon_512))
        {
          throw new ValidationException("Only falcon_512 allowed for FALCON keys");
        }
      }
      if (algo == null)
      {
        throw new ValidationException(String.format("Unknown sig type %d", sig_type));
      }
      PublicKey pub_key = KeyUtil.decodeKey(encoded, algo, sig_type);
      System.out.println("KeyClass: " + pub_key.getClass());
      return KeyUtil.decodeKey(encoded, algo, sig_type);
    }


  }

  public static boolean checkSignature(SigSpec sig_spec, ByteString signed_data, ByteString signature)
    throws ValidationException
  {
    int sig_type = sig_spec.getSignatureType();

    PublicKey pub_key = decodePublicKey(sig_spec);
    String algo=getAlgo(sig_type);

    /*if (algo.equals("ECDSA"))
    {
      algo="SHA1WithECDSA";
    }*/

    try
    {

      Signature sig_engine = Signature.getInstance(algo, Globals.getCryptoProviderName());
      sig_engine.initVerify(pub_key);
      sig_engine.update(signed_data.toByteArray());

      return sig_engine.verify(signature.toByteArray());
    }
    catch(Exception e)
    {
      throw new ValidationException(e);
    }
  }

  public static String getAlgo(int sig_type)
    throws ValidationException
  {
    String algo="";

    if (sig_type == SIG_TYPE_ECDSA_COMPRESSED)
    {
      algo="ECDSA";
    }
    if (sig_type == SIG_TYPE_ECDSA)
    {
      algo="ECDSA";
    }
    if (sig_type == SIG_TYPE_DSA)
    {
      algo="DSA";
    }
    if (sig_type == SIG_TYPE_RSA)
    {
      algo="RSA";
    }
    if (sig_type == SIG_TYPE_DSTU4145)
    {
      algo="DSTU4145";
    }
    if (sig_type == SIG_TYPE_SPHINCSPLUS)
    {
      algo="SPHINCSPLUS";
    }
    if (sig_type == SIG_TYPE_DILITHIUM)
    {
      algo="DILITHIUM";
    }
    if (sig_type == SIG_TYPE_FALCON)
    {
      algo="FALCON";
    }

    if (algo == null)
    {
      throw new ValidationException(String.format("Unknown sig type %d", sig_type));
    }
    return algo;
  }

  /**
   * These estimates could be way off, especially for RSA which entirely
   * depends on key size.  But gives an idea for fee estimation.
   */
  public static int estimateSignatureBytes(int sig_type)
    throws ValidationException
  {
    if (sig_type == SIG_TYPE_ECDSA_COMPRESSED)
    {
      return 71;
    }
    if (sig_type == SIG_TYPE_ECDSA)
    {
      return 120;
    }
    if (sig_type == SIG_TYPE_DSA)
    {
      return 100;
    }
    if (sig_type == SIG_TYPE_RSA)
    {
      return 8192/8;
    }
    if (sig_type == SIG_TYPE_DSTU4145)
    {
      return 90;
    }
    if (sig_type == SIG_TYPE_SPHINCSPLUS)
    {
      return 7856;
    }
    if (sig_type == SIG_TYPE_DILITHIUM)
    {
      return 4595;
    }
    if (sig_type == SIG_TYPE_FALCON)
    {
      return 1274;
    }

    throw new ValidationException(String.format("Unknown sig type %d", sig_type));

  }

   public static ByteString sign(WalletKeyPair key_pair, ByteString data)
    throws ValidationException
  {

    int sig_type = key_pair.getSignatureType();
    String algo=getAlgo(sig_type);

    PrivateKey priv_key = KeyUtil.decodePrivateKey(key_pair.getPrivateKey(), algo);

    try
    {
      Signature sig_engine = Signature.getInstance(algo, Globals.getCryptoProviderName());
      sig_engine.initSign(priv_key);
      sig_engine.update(data.toByteArray());

      return ByteString.copyFrom(sig_engine.sign());
    }
    catch(java.security.NoSuchAlgorithmException e)
    {
      throw new RuntimeException(e);
    }
    catch(java.security.GeneralSecurityException e)
    {
      throw new ValidationException(e);
    }

  }


  public static ByteString sign(WalletKeyPair key_pair, ChainHash data)
    throws ValidationException
  {
    return sign(key_pair, data.getBytes());
  }

  public static ImmutableSet<String> ALLOWED_ECDSA_CURVES = getAllowedECDSACurves();
  public static ImmutableSet<String> ALLOWED_DSTU4145_CURVES = getAllowedDSTU4145Curves();

  private static ImmutableSet<String> getAllowedECDSACurves()
  {
    return ImmutableSet.of(
      "1.3.132.0.10", //secp256k1
      "1.3.132.0.34", //secp384r1
      "1.3.132.0.35", //secp521r1
      "1.3.132.0.38", //sect571k1
      "1.3.132.0.39"  //sect571r1
      );

  }

  public static ImmutableSet<String> getAllowedDSTU4145Curves()
  {
    Set<String> s= new TreeSet<String>();
    // 0 to 9 maps to DSTU 4145-163 to DSTU 4145-431.
    for(int i=0; i<=9; i++)
    {
      s.add("1.2.804.2.1.1.1.1.3.1.1.2." + i);
    }

    return ImmutableSet.copyOf(s);
  }

  public static ImmutableSet<String> getAllowedDilithiumOIDs()
  {
    Set<String> s= new TreeSet<String>();

    return ImmutableSet.copyOf(s);


  }

}
