package snowblossom;

import snowblossom.proto.SigSpec;

import com.google.protobuf.ByteString;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

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
  {
    //TODO - OMG IMPLEMENT 
    return true;
  }

  public static ImmutableSet<String> ALLOWED_ECDSA_CURVES = getAllowedECDSACurves();

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



  

}
