package snowblossom.lib;

import com.google.protobuf.ByteString;
import java.security.Key;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.spec.IESParameterSpec;
import snowblossom.proto.SigSpec;
import snowblossom.proto.WalletKeyPair;
import snowblossom.util.proto.SymmetricKey;

public class CipherUtil
{

  // algo_set zero
  // Selecting AES/256 as it is very commonly used and respected
  // using CBC because we don't need GCM as these messages will probably
  // always be signed separately so don't need auth and GCM may leak key
  // data if the same IV is used.  We shouldn't use the same IV, but just in case
  // CBC seems a safer choice.
  public static final String SYM_ENCRYPTION_MODE_0 = "AES/CBC/PKCS5PADDING";
  public static final int SYM_BLOCK_SIZE_0 = 32; // 256 bits
  public static final int SYM_IV_SIZE_0 = 16;

  public static ByteString encrypt(SigSpec sig_spec, ByteString plain_data)
    throws ValidationException
  {
    try
    {
      int sig_type = sig_spec.getSignatureType();
      ByteString encoded = sig_spec.getPublicKey();
      String algo = SignatureUtil.getAlgo(sig_type);

      PublicKey pub_key = SignatureUtil.decodePublicKey(sig_spec);

      Cipher c = null;
      if (algo.equals("ECDSA"))
      {
        // DANGER - IES paremeters here copied from the internet without understanding.
        // Are these the right/reasonable values?
        // Are we just worried about duplicates/collisions with d and e?
        // If so, we can probably safely reduce their sizes a bit.
        // What about existing things using older versions of BC ECIES that didn't use
        // params?  Will we be able to decrypt those?  What parameters were they using?
        // Should we pass along a version number or something for future parameter changes?
        // Basically, this shouldn't be used without further exploration.
        Random rnd_low = new Random();
        byte[] d = new byte[16];
        byte[] e = new byte[16];

        rnd_low.nextBytes(d);
        rnd_low.nextBytes(e);

        c = Cipher.getInstance("ECIES","BC");
        c.init(Cipher.ENCRYPT_MODE, pub_key, new IESParameterSpec(d,e,128));

        return ByteString.copyFrom(d)
          .concat(ByteString.copyFrom(e))
          .concat(ByteString.copyFrom(c.doFinal(plain_data.toByteArray())));
      }
      else
      {
        throw new ValidationException("Encryption not supported with " + algo);
      }


    }
    catch(java.security.GeneralSecurityException e)
    {
      throw new ValidationException(e);
    }
  }

  public static ByteString decrypt(WalletKeyPair wkp, ByteString cipher_data)
    throws ValidationException
  {
    try
    {
      String algo = SignatureUtil.getAlgo(wkp.getSignatureType());
      KeyPair kp = KeyUtil.decodeKeypair(wkp);
      Cipher c = null;


      if (algo.equals("ECDSA"))
      {
        ByteString d = cipher_data.substring(0,16);
        ByteString e = cipher_data.substring(16,32);
        ByteString ct = cipher_data.substring(32);

        c = Cipher.getInstance("ECIES","BC");
        c.init(Cipher.DECRYPT_MODE, kp.getPrivate(), new IESParameterSpec(d.toByteArray(),e.toByteArray(),128));
        return ByteString.copyFrom(c.doFinal(ct.toByteArray()));
      }
      else
      {
        throw new ValidationException("Encryption not supported with " + algo);
      }

    }
    catch(java.security.GeneralSecurityException e)
    {
      throw new ValidationException(e);
    }
  }

  public static SymmetricKey generageSymmetricKey()
  {
    SecureRandom rnd = new SecureRandom();

    SymmetricKey.Builder key = SymmetricKey.newBuilder();

    key.setAlgoSet(0);

    byte[] key_data = new byte[SYM_BLOCK_SIZE_0];
    rnd.nextBytes(key_data);
    key.setKey( ByteString.copyFrom(key_data) );

    // Probably overly paranoid, but as the key_id will be shared
    // using a new low security rnd to not give away any state information
    // from the secure rnd
    Random rnd_low = new Random();
    byte[] key_id_data = new byte[8];
    rnd_low.nextBytes(key_id_data);
    key.setKeyId( ByteString.copyFrom(key_id_data) );
    return key.build();

  }

  public static ByteString encryptSymmetric(SymmetricKey key, ByteString plain_data)
    throws ValidationException
  {
    // Don't need secure random for IV
    Random rnd = new Random();

    try
    {
      if (key.getAlgoSet() == 0)
      {
        byte[] iv_bytes = new byte[SYM_IV_SIZE_0];
        rnd.nextBytes(iv_bytes);

        Key k_spec = new SecretKeySpec(key.getKey().toByteArray(), "AES");
        Cipher cipher = Cipher.getInstance(SYM_ENCRYPTION_MODE_0);
        cipher.init(Cipher.ENCRYPT_MODE, k_spec, new IvParameterSpec(iv_bytes));

        byte[] cipher_data = cipher.doFinal(plain_data.toByteArray());

        return ByteString.copyFrom(iv_bytes).concat(ByteString.copyFrom(cipher_data));
      }
      throw new ValidationException("Unknown algo_set: " + key.getAlgoSet());
    }
    catch(java.security.GeneralSecurityException e)
    {
      throw new ValidationException(e);
    }
  }

  public static ByteString encryptSymmetric(SymmetricKey key, ByteString plain_data, ByteString iv)
    throws ValidationException
  {
    try
    {
      if (key.getAlgoSet() == 0)
      {

        Key k_spec = new SecretKeySpec(key.getKey().toByteArray(), "AES");
        Cipher cipher = Cipher.getInstance(SYM_ENCRYPTION_MODE_0);
        cipher.init(Cipher.ENCRYPT_MODE, k_spec, new IvParameterSpec(iv.toByteArray()));

        byte[] cipher_data = cipher.doFinal(plain_data.toByteArray());

        return iv.concat(ByteString.copyFrom(cipher_data));
      }
      throw new ValidationException("Unknown algo_set: " + key.getAlgoSet());
    }
    catch(java.security.GeneralSecurityException e)
    {
      throw new ValidationException(e);
    }
  }


  public static ByteString decryptSymmetric(SymmetricKey key, ByteString cipher_data)
    throws ValidationException
  {
    try
    {
      if (key.getAlgoSet() == 0)
      {
        byte[] iv_bytes = cipher_data.substring(0, SYM_IV_SIZE_0).toByteArray();

        Key k_spec = new SecretKeySpec(key.getKey().toByteArray(), "AES");
        Cipher cipher = Cipher.getInstance(SYM_ENCRYPTION_MODE_0);
        cipher.init(Cipher.DECRYPT_MODE, k_spec, new IvParameterSpec(iv_bytes));

        byte[] plain_data = cipher.doFinal(cipher_data.substring(SYM_IV_SIZE_0).toByteArray());
        return ByteString.copyFrom(plain_data);

      }
      throw new ValidationException("Unknown algo_set: " + key.getAlgoSet());

    }
    catch(java.security.GeneralSecurityException e)
    {
      throw new ValidationException(e);
    }

  }

}
