package snowblossom.lib;

import com.google.protobuf.ByteString;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import org.bouncycastle.asn1.*;
import org.junit.Assert;
import snowblossom.proto.WalletKeyPair;
import org.bouncycastle.pqc.jcajce.spec.SPHINCSPlusParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec;


public class KeyUtil
{
  public static ByteString EC_SECP256K1_PREFIX= HexUtil.hexStringToBytes("3036301006072a8648ce3d020106052b8104000a032200");

  public static PublicKey convertCompressedECDSA(ByteString encoded)
    throws ValidationException
  {
    if (encoded.size()!=33)
    {
      throw new ValidationException("Compressed key must be exactly 33 bytes");
    }

    ByteString fullkey = EC_SECP256K1_PREFIX.concat(encoded);

    return decodeKey(fullkey, "ECDSA", SignatureUtil.SIG_TYPE_ECDSA);

  }

  public static ByteString getCompressedPublicKeyEncoding(PublicKey key)
  {
    ByteString full = ByteString.copyFrom(key.getEncoded());
    Assert.assertTrue(full.startsWith(EC_SECP256K1_PREFIX));
    Assert.assertEquals( full.size(), EC_SECP256K1_PREFIX.size() + 33);

    return full.substring(EC_SECP256K1_PREFIX.size());
  }

  public static KeyPair decodeKeypair(WalletKeyPair wkp)
    throws ValidationException
  {
    String algo = SignatureUtil.getAlgo(wkp.getSignatureType());
    return new KeyPair( decodeKey( wkp.getPublicKey(), algo, wkp.getSignatureType()), decodePrivateKey(wkp.getPrivateKey(), algo));
  }

  public static PublicKey decodeKey(ByteString encoded, String algo, int sig_type)
    throws ValidationException
  {
    if (sig_type == SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED)
    {
      return convertCompressedECDSA(encoded);
    }

    try
    {
      X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded.toByteArray());
      KeyFactory fact = KeyFactory.getInstance(algo, Globals.getCryptoProviderName());
      return fact.generatePublic(spec);
    }
    catch(java.security.GeneralSecurityException e)
    {
      throw new ValidationException("Error decoding key", e);
    }
  }

  public static PrivateKey decodePrivateKey(ByteString encoded, String algo)
    throws ValidationException
  {
    try
    {
      PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encoded.toByteArray());
      KeyFactory fact = KeyFactory.getInstance(algo, Globals.getCryptoProviderName());
      return fact.generatePrivate(spec);
    }
    catch(java.security.GeneralSecurityException e)
    {
      throw new ValidationException("Error decoding key", e);
    }

  }

  /**
   * Get the EC curve parameters used by the secp256k1 keys used for HD seeds
   */
  public static ECParameterSpec getECHDSpec()
  {
    try
    {
      ECGenParameterSpec spec = new ECGenParameterSpec("secp256k1");
      KeyPairGenerator key_gen = KeyPairGenerator.getInstance("ECDSA", Globals.getCryptoProviderName());
      key_gen.initialize(spec);

      KeyPair pair = key_gen.genKeyPair();
      ECPrivateKey priv = (ECPrivateKey)pair.getPrivate();

      return priv.getParams();
    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }

  }


  public static KeyPair generateECCompressedKey()
  {
    try
    {
      ECGenParameterSpec spec = new ECGenParameterSpec("secp256k1");
      KeyPairGenerator key_gen = KeyPairGenerator.getInstance("ECDSA", Globals.getCryptoProviderName());
      key_gen.initialize(spec);

      KeyPair pair = key_gen.genKeyPair();

      if (pair.getPublic() instanceof org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey)
      {
        org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey pub = (org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey) pair.getPublic();
        pub.setPointFormat("COMPRESSED");
      }
      else
      {
        throw new Exception("Unable to set public point format to compressed.  Not from SC or BC provider");
      }

      return pair;
    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  public static WalletKeyPair generateWalletStandardECKey()
  {
    KeyPair key_pair = KeyUtil.generateECCompressedKey();
    ByteString public_encoded = KeyUtil.getCompressedPublicKeyEncoding(key_pair.getPublic());

    WalletKeyPair wkp = WalletKeyPair.newBuilder()
      .setPublicKey(KeyUtil.getCompressedPublicKeyEncoding(key_pair.getPublic()))
      .setPrivateKey(ByteString.copyFrom(key_pair.getPrivate().getEncoded()))
      .setSignatureType(SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED)
      .build();
    return wkp;
  }

  public static WalletKeyPair generateWalletECKey(String curve_name)
  {
    try
    {

      ECGenParameterSpec spec = new ECGenParameterSpec(curve_name);

      KeyPairGenerator key_gen = KeyPairGenerator.getInstance("ECDSA", Globals.getCryptoProviderName());
      key_gen.initialize(spec);
      KeyPair pair = key_gen.genKeyPair();

      if (pair.getPublic() instanceof org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey)
      {
        org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey pub = (org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey) pair.getPublic();
        pub.setPointFormat("COMPRESSED");
      }
      else
      {
        throw new Exception("Unable to set public point format to compressed.  Not from SC or BC provider");
      }

      WalletKeyPair wkp = WalletKeyPair.newBuilder()
        .setPublicKey(ByteString.copyFrom(pair.getPublic().getEncoded()))
        .setPrivateKey(ByteString.copyFrom(pair.getPrivate().getEncoded()))
        .setSignatureType(SignatureUtil.SIG_TYPE_ECDSA)
        .build();
      return wkp;
    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  public static WalletKeyPair generateWalletRSAKey(int key_len)
  {
    try
    {
      RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(key_len, RSAKeyGenParameterSpec.F4);

      KeyPairGenerator key_gen = KeyPairGenerator.getInstance("RSA", Globals.getCryptoProviderName());

      key_gen.initialize(spec);
      KeyPair key_pair = key_gen.genKeyPair();

      WalletKeyPair wkp = WalletKeyPair.newBuilder()
        .setPublicKey(ByteString.copyFrom(key_pair.getPublic().getEncoded()))
        .setPrivateKey(ByteString.copyFrom(key_pair.getPrivate().getEncoded()))
        .setSignatureType(SignatureUtil.SIG_TYPE_RSA)
        .build();
      return wkp;
    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }
  }
  public static WalletKeyPair generateWalletDSAKey()
  {
    try
    {
      KeyPairGenerator key_gen = KeyPairGenerator.getInstance("DSA", Globals.getCryptoProviderName());

      key_gen.initialize(3072);

      KeyPair key_pair = key_gen.genKeyPair();

      WalletKeyPair wkp = WalletKeyPair.newBuilder()
        .setPublicKey(ByteString.copyFrom(key_pair.getPublic().getEncoded()))
        .setPrivateKey(ByteString.copyFrom(key_pair.getPrivate().getEncoded()))
        .setSignatureType(SignatureUtil.SIG_TYPE_DSA)
        .build();
      return wkp;
    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }
  }
  public static WalletKeyPair generateWalletDSTU4145Key(int curve)
  {
    try
    {
      ECGenParameterSpec spec = new ECGenParameterSpec("1.2.804.2.1.1.1.1.3.1.1.2." + curve);

      KeyPairGenerator key_gen = KeyPairGenerator.getInstance("DSTU4145", Globals.getCryptoProviderName());

      key_gen.initialize(spec);
      KeyPair key_pair = key_gen.genKeyPair();

      WalletKeyPair wkp = WalletKeyPair.newBuilder()
        .setPublicKey(ByteString.copyFrom(key_pair.getPublic().getEncoded()))
        .setPrivateKey(ByteString.copyFrom(key_pair.getPrivate().getEncoded()))
        .setSignatureType(SignatureUtil.SIG_TYPE_DSTU4145)
        .build();
      return wkp;
    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  public static WalletKeyPair generateWalletSphincsPlusKey()
  {
    try
    {
      
      KeyPairGenerator key_gen = KeyPairGenerator.getInstance("SPHINCSPLUS", Globals.getCryptoProviderName());

      // 128bit -> 17kb signature
      // 256bit -> 30kb signature
      key_gen.initialize(SPHINCSPlusParameterSpec.shake_256f_simple);

      KeyPair key_pair = key_gen.genKeyPair();
      WalletKeyPair wkp = WalletKeyPair.newBuilder()
        .setPublicKey(ByteString.copyFrom(key_pair.getPublic().getEncoded()))
        .setPrivateKey(ByteString.copyFrom(key_pair.getPrivate().getEncoded()))
        .setSignatureType(SignatureUtil.SIG_TYPE_SPHINCSPLUS)
        .build();
      return wkp;

    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }

  }

  public static WalletKeyPair generateWalletDilithiumKey()
  {
    try
    {
      
      KeyPairGenerator key_gen = KeyPairGenerator.getInstance("DILITHIUM", Globals.getCryptoProviderName());

      key_gen.initialize(DilithiumParameterSpec.dilithium5);

      KeyPair key_pair = key_gen.genKeyPair();
      WalletKeyPair wkp = WalletKeyPair.newBuilder()
        .setPublicKey(ByteString.copyFrom(key_pair.getPublic().getEncoded()))
        .setPrivateKey(ByteString.copyFrom(key_pair.getPrivate().getEncoded()))
        .setSignatureType(SignatureUtil.SIG_TYPE_DILITHIUM)
        .build();
      return wkp;

    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }

  }



}
