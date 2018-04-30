package snowblossom;

import snowblossom.proto.SigSpec;

import com.google.protobuf.ByteString;

import com.google.common.collect.ImmutableSet;

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
  {
    //TODO - OMG IMPLEMENT 
    return true;
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
