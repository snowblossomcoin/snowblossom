package client.test;

import com.google.protobuf.ByteString;
import java.util.Random;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import snowblossom.client.PasswordCrypt;
import snowblossom.lib.Globals;

public class PasswordCryptTest
{

  @BeforeClass
  public static void loadProvider()
  {
    Globals.addCryptoProvider();
  }

  @Test
  public void basicEncryptTest()
    throws Exception
  {
    Random rnd = new Random();
    for(int i=0; i<5; i++)
    {
      int len = 1+rnd.nextInt(100000);
      byte[] in_data = new byte[len];
      rnd.nextBytes(in_data);

      ByteString enc_data = PasswordCrypt.encrypt(ByteString.copyFrom(in_data), "test_pass_1", "scrypt");

      ByteString result = PasswordCrypt.decrypt(enc_data, "test_pass_1");
      Assert.assertEquals(ByteString.copyFrom(in_data), result);
    }
  }

  @Test
  public void badPassTest()
    throws Exception
  {
    Random rnd = new Random();
    byte[] in_data = new byte[8192];
    rnd.nextBytes(in_data);
    
    ByteString enc_data = PasswordCrypt.encrypt(ByteString.copyFrom(in_data), "test_pass_1", "scrypt");

    ByteString result = PasswordCrypt.decrypt(enc_data, "test_pass_2");

    Assert.assertNull(result);
  }

  @Test
  public void badDataTest()
    throws Exception
  {
    Random rnd = new Random();

    for(int i=0; i<100; i++)
    {
      int len = 16+rnd.nextInt(100000);

      byte[] in_data = new byte[len];
      rnd.nextBytes(in_data);

      ByteString result = PasswordCrypt.decrypt(ByteString.copyFrom(in_data), "test_pass_1");
      
      Assert.assertNull(result);
    }

  }




}
