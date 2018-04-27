package snowblossom;

import org.junit.Assert;
import org.junit.Test;
import org.junit.BeforeClass;

import java.security.MessageDigest;

import snowblossom.proto.SigSpec;
import snowblossom.proto.AddressSpec;

import java.util.Random;

import com.google.protobuf.ByteString;
import java.nio.ByteBuffer;

public class AddressUtilTest
{
  @BeforeClass
  public static void loadProvider()
  {
    Globals.addCryptoProvider();
  }


  @Test
  public void testRepeat()
  {
    for(int i=0; i<200; i++)
    {
      testBasicSpecHash();
    }
  }

  @Test
  public void testBasicSpecHash()
  {

    AddressSpec.Builder b = AddressSpec.newBuilder();

    ByteBuffer bb = ByteBuffer.allocate(1024*1024);

    Random rnd = new Random();

    int keys = rnd.nextInt(100);
    int required = 0;
    if (keys > 0) required = rnd.nextInt(keys) + 1;

    b.setRequiredSigners(required);
    bb.putInt(required);
    bb.putInt(keys);

    for(int i=0; i<keys; i++)
    {
      byte[] public_key = new byte[rnd.nextInt(800)];
      rnd.nextBytes(public_key);
      int sig_type = rnd.nextInt(12);

      bb.putInt(sig_type);
      bb.putInt(public_key.length);
      bb.put(public_key);

      SigSpec ss = SigSpec.newBuilder()
        .setSignatureType(sig_type)
        .setPublicKey(ByteString.copyFrom(public_key))
        .build();

      b.addSigSpecs(ss);
    }

    AddressSpecHash found_hash = AddressUtil.getHashForSpec(b.build(),  DigestUtil.getMDAddressSpec());

    MessageDigest md = DigestUtil.getMDAddressSpec();
    md.update(bb.array(), 0, bb.position());

    byte[] h = md.digest();

    AddressSpecHash expected_hash = new AddressSpecHash(h);

    Assert.assertEquals(expected_hash, found_hash);

    System.out.println("" + found_hash + " " + expected_hash);
  }
  
}
