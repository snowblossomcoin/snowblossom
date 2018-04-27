package snowblossom;

import snowblossom.proto.SigSpec;

import com.google.protobuf.ByteString;

public class SignatureUtil
{
  public static final int SIG_TYPE_ECDSA=1; //secp256k1 of course
  public static final int SIG_TYPE_ECDSA_COMPRESSED=2; //also secp256k1
  public static final int SIG_TYPE_DSA=3;
  public static final int SIG_TYPE_RSA=4;
  public static final int SIG_TYPE_DSTU4145=5;

  public static boolean checkSignature(SigSpec sig_spec, ByteString signed_data, ByteString signature)
  {
    //TODO - OMG IMPLEMENT 
    return true;
  }

}
