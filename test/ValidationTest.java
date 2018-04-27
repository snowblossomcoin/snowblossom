package snowblossom;

import org.junit.Test;
import org.junit.Assert;

import com.google.protobuf.ByteString;

public class ValidationTest
{


  @Test(expected = ValidationException.class)
  public void testAddrSpecEmptyString()
    throws Exception
  {
    byte[] empty=new byte[0];
    ByteString bs = ByteString.copyFrom(empty);

    Validation.validateAddressSpecHash(bs, "empty");
  }

  @Test(expected = ValidationException.class)
  public void testAddrSpecShortString()
    throws Exception
  {
    byte[] b=new byte[12];
    ByteString bs = ByteString.copyFrom(b);

    Validation.validateAddressSpecHash(bs, "short");
  }
  @Test(expected = ValidationException.class)
  public void testAddrSpecNullString()
    throws Exception
  {
    Validation.validateAddressSpecHash(null, "short");
  }

  @Test
  public void testAddrSpecCorrectString()
    throws Exception
  {
    byte[] b=new byte[Globals.ADDRESS_SPEC_HASH_LEN];
    ByteString bs = ByteString.copyFrom(b);

    Validation.validateAddressSpecHash(bs, "correct");
  }
 
  @Test
  public void testChainHashCorrectString()
    throws Exception
  {
    byte[] b=new byte[Globals.BLOCKCHAIN_HASH_LEN];
    ByteString bs = ByteString.copyFrom(b);

    Validation.validateChainHash(bs, "correct");
  }
 
}
