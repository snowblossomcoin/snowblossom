package snowblossom.client;

import com.google.protobuf.ByteString;
import com.lambdaworks.crypto.SCrypt;
import java.security.Key;
import java.security.MessageDigest;
import java.util.Random;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import snowblossom.proto.*;

/**
 * Simple, but hopefully secure encrypting and decrypting of data
 */
public class PasswordCrypt
{
  private static final Logger logger = Logger.getLogger("snowblossom.client");

  private static TreeMap<String, ByteString> scrypt_params_to_key_map = new TreeMap<>();

  public static final int SCRYPT_MEMORY_COST=2;
  public static final int SCRYPT_PARALLELIZATION_COST=128;
  public static final int SCRYPT_CPU_COST=128*1024;
  public static final String SCRYPT_SALT="snowblossom";
  public static final String ENCRYPTION_MODE = "AES/CBC/PKCS5PADDING";
  public static final int BLOCK_SIZE = 16;

  /**
   * Attempts to parse the input file as a snowblossom.proto.EncryptedFile and decrypt with password.
   * Only returns non-null if the checksum matches inside the encrypted payload
   *
   * @returns null on any trouble
   */
  public static ByteString decrypt(ByteString input, String password)
  {
    try
    {
      EncryptedFile outer = EncryptedFile.parseFrom(input);
      String function = outer.getFunction();
      if (!function.equals("scrypt"))
      {
        throw new RuntimeException("Unknown function: " + function);
      }
      ByteString key = getScryptKey(password, outer, BLOCK_SIZE);
      ByteString iv = outer.getIv();

      Key k = new SecretKeySpec(key.toByteArray(), "AES");

      Cipher cipher = Cipher.getInstance(ENCRYPTION_MODE);

      cipher.init(Cipher.DECRYPT_MODE, k, new IvParameterSpec(iv.toByteArray()));

      ByteString outer_payload = ByteString.copyFrom( cipher.doFinal( outer.getPayload().toByteArray() ) );

      EncryptedFilePayload inner = EncryptedFilePayload.parseFrom(outer_payload);

      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(inner.getPayload().toByteArray());
      ByteString hash_result = ByteString.copyFrom(md.digest());

      if (hash_result.equals(inner.getSha256Hash()))
      {
        return inner.getPayload();
      }

    }
    catch(com.google.protobuf.InvalidProtocolBufferException e)
    {
      // This will happen if the outer data isn't actually an EncryptedFile
      // or we have the wrong key (based on wrong password) and can't parse the inner payload
      return null;
    }
    catch(java.security.GeneralSecurityException e)
    {
      // If we have the wrong key, this could be a passing error (very likely)
      return null;
    }

    // This is pretty much if everything decrytped and parsed, but the checksum doesn't match
    // could possibly be the wrong key but by dumb luck the padding doesn't have an error
    // and the protobuf somehow parsed (which might be easy, protobuf is pretty terse)
    return null;
  }


  public static ByteString encrypt(ByteString input, String password, String function)
  {
    if (!function.equals("scrypt"))
    {
      throw new RuntimeException("Unknown function: " + function);
    }
    try
    {
     
      // The inner object that gets encrypted
      // simple has the data payload and a hash of it
      EncryptedFilePayload.Builder inner = EncryptedFilePayload.newBuilder();
      inner.setPayload(input);

      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(input.toByteArray());
      inner.setSha256Hash(ByteString.copyFrom(md.digest()));

      ByteString data_payload = inner.build().toByteString();

      // The outer data object, has the encrypted payload and parameter information
      EncryptedFile.Builder file = EncryptedFile.newBuilder();

      file.setFunction(function);
      file.setScryptMemoryCost(SCRYPT_MEMORY_COST);
      file.setScryptParallelizationCost(SCRYPT_PARALLELIZATION_COST);
      file.setScryptCpuCost(SCRYPT_CPU_COST);

      ByteString key = getScryptKey(password, file.build(), BLOCK_SIZE);

      Cipher cipher = Cipher.getInstance(ENCRYPTION_MODE);

      Random rnd = new Random();
      byte[] iv = new byte[BLOCK_SIZE];
      rnd.nextBytes(iv);

      Key k = new SecretKeySpec(key.toByteArray(), "AES");

      file.setIv(ByteString.copyFrom(iv));

      cipher.init(Cipher.ENCRYPT_MODE, k, new IvParameterSpec(iv));

      file.setPayload( ByteString.copyFrom( cipher.doFinal( data_payload.toByteArray() ) ) );

      return file.build().toByteString();

    }
    catch(java.security.NoSuchAlgorithmException e)
    {
      throw new RuntimeException(e);
    }
    catch(java.security.GeneralSecurityException e)
    {
      throw new RuntimeException(e);
    }

  }

  /**
   * Get the key bytes based on a password using the params in EncryptedFile.
   * Cache the result so we can decrypt/encrypt multiple files with the same
   * password quickly
   */
  protected static ByteString getScryptKey(String pass, EncryptedFile params, int key_len)
  {
    String param_token = String.format("%s.%d.%d.%d.%d", pass, params.getScryptCpuCost(),
      params.getScryptMemoryCost(),
      params.getScryptParallelizationCost(),
      key_len);

    synchronized(scrypt_params_to_key_map)
    {
      if (scrypt_params_to_key_map.containsKey(param_token))
      {
        return scrypt_params_to_key_map.get(param_token);
      }
    }

    try
    {
      long t1 = System.currentTimeMillis();
      byte[] key = SCrypt.scrypt(pass.getBytes(), SCRYPT_SALT.getBytes(),
          params.getScryptCpuCost(),
          params.getScryptMemoryCost(),
          params.getScryptParallelizationCost(),
          key_len);
      long t2 = System.currentTimeMillis();

      logger.log(Level.FINE, "Scrypt gen took: " + (t2 - t1) + " ms");

      ByteString key_bs = ByteString.copyFrom(key);

      synchronized(scrypt_params_to_key_map)
      {
        scrypt_params_to_key_map.put(param_token, key_bs);
      }

      return key_bs;

    }
    catch(java.security.GeneralSecurityException e)
    {
      throw new RuntimeException(e);
    }
  }

}
