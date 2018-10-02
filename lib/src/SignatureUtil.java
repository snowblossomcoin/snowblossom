package snowblossom.lib;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import snowblossom.proto.SigSpec;
import snowblossom.proto.WalletKeyPair;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

public class SignatureUtil
{
  /**
   * The public key sent on the network shall be just "03" plus the 32 bytes of X
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

  public static boolean checkSignature(SigSpec sig_spec, ByteString signed_data, ByteString signature)
    throws ValidationException
  {
    int sig_type = sig_spec.getSignatureType();
    ByteString encoded = sig_spec.getPublicKey();

    PublicKey pub_key = null;
    String algo="";

    if (sig_type == SIG_TYPE_ECDSA_COMPRESSED)
    {
      pub_key = KeyUtil.convertCompressedECDSA(encoded);
      algo="ECDSA";
    }
    else
    {
      ArrayList<String> oidList = KeyUtil.extractObjectIdentifiers(encoded);

      if (sig_type == SIG_TYPE_ECDSA)
      {
        algo="ECDSA";
        if (oidList.size() != 2) throw new ValidationException("Unexpected number of OIDs in public key");
        if (!ALLOWED_ECDSA_CURVES.contains(oidList.get(1))) throw new ValidationException(
          String.format("OID %s not on allowed list for %s", oidList.get(1), algo)); 
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
        if (oidList.size() != 2) throw new ValidationException("Unexpected number of OIDs in public key");
        if (!ALLOWED_DSTU4145_CURVES.contains(oidList.get(1))) throw new ValidationException(
          String.format("OID %s not on allowed list for %s", oidList.get(1), algo)); 
      }
      if (algo == null)
      {
        throw new ValidationException(String.format("Unknown sig type %d", sig_type));
      }
      pub_key = KeyUtil.decodeKey(encoded, algo);
    }

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
      return 70;
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

}
