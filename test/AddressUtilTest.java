package snowblossom;

import com.google.protobuf.ByteString;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import snowblossom.proto.AddressSpec;
import snowblossom.proto.SigSpec;
import snowblossomlib.AddressSpecHash;
import snowblossomlib.AddressUtil;
import snowblossomlib.DigestUtil;
import snowblossomlib.Duck32;
import snowblossomlib.Globals;
import snowblossomlib.NetworkParams;
import snowblossomlib.NetworkParamsProd;
import snowblossomlib.NetworkParamsRegtest;
import snowblossomlib.NetworkParamsTestnet;
import snowblossomlib.ValidationException;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;

public class AddressUtilTest
{
  @BeforeClass
  public static void loadProvider()
  {
    snowblossomlib.Globals.addCryptoProvider();
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

    snowblossomlib.AddressSpecHash found_hash = AddressUtil.getHashForSpec(b.build(), snowblossomlib.DigestUtil.getMDAddressSpec());

    MessageDigest md = DigestUtil.getMDAddressSpec();
    md.update(bb.array(), 0, bb.position());

    byte[] h = md.digest();

    snowblossomlib.AddressSpecHash expected_hash = new snowblossomlib.AddressSpecHash(h);

    Assert.assertEquals(expected_hash, found_hash);

    System.out.println("" + found_hash + " " + expected_hash);
  }

  @Test
  public void testAddressConversions()
    throws snowblossomlib.ValidationException
  {
    ArrayList<snowblossomlib.NetworkParams> plist = new ArrayList<snowblossomlib.NetworkParams>();
    plist.add(new NetworkParamsProd());
    plist.add(new NetworkParamsTestnet());
    plist.add(new NetworkParamsRegtest());

    byte[] buff = new byte[snowblossomlib.Globals.ADDRESS_SPEC_HASH_LEN];
    Random rnd = new Random();
    HashSet<String> addresses = new HashSet<>();

    for(int i=0; i<1000; i++)
    {
      if (i > 0)
      {
        rnd.nextBytes(buff);
      }

      snowblossomlib.AddressSpecHash spec = new snowblossomlib.AddressSpecHash(buff);
      for(NetworkParams p : plist)
      {
        String addr = spec.toAddressString(p);
        Assert.assertFalse(addresses.contains(addr));
        addresses.add(addr);
        System.out.println("Address: "  + addr);

        snowblossomlib.AddressSpecHash dec = new snowblossomlib.AddressSpecHash(addr, p);
        Assert.assertEquals(spec, dec);


        int colon = addr.indexOf(":");
        String without = addr.substring(colon+1);

        snowblossomlib.AddressSpecHash dec2 = new snowblossomlib.AddressSpecHash(without, p);
        Assert.assertEquals(spec, dec2);
      }
    }
  }

  @Test(expected= snowblossomlib.ValidationException.class)
  public void testAddressChecksumWrongLabel()
    throws Exception
  {
    Random rnd = new Random();
    byte[] buff = new byte[snowblossomlib.Globals.ADDRESS_SPEC_HASH_LEN];
    rnd.nextBytes(buff);
    snowblossomlib.AddressSpecHash spec = new snowblossomlib.AddressSpecHash(buff);

    String addr = snowblossomlib.Duck32.encode("d1", spec.getBytes());

    int colon = addr.indexOf(":");
    String without = addr.substring(colon+1);

    snowblossomlib.Duck32.decode("d2", without);
  }

  @Test(expected= snowblossomlib.ValidationException.class)
  public void testAddressChecksumChangeLabelPrefix()
    throws Exception
  {
    Random rnd = new Random();
    byte[] buff = new byte[snowblossomlib.Globals.ADDRESS_SPEC_HASH_LEN];
    rnd.nextBytes(buff);
    snowblossomlib.AddressSpecHash spec = new snowblossomlib.AddressSpecHash(buff);

    String addr = snowblossomlib.Duck32.encode("d2", spec.getBytes());

    snowblossomlib.Duck32.decode("d1", addr);
  }
 
  @Test(expected= snowblossomlib.ValidationException.class)
  public void testAddressChecksumChangeLabel()
    throws Exception
  {
    Random rnd = new Random();
    byte[] buff = new byte[snowblossomlib.Globals.ADDRESS_SPEC_HASH_LEN];
    rnd.nextBytes(buff);
    snowblossomlib.AddressSpecHash spec = new snowblossomlib.AddressSpecHash(buff);

    String addr = snowblossomlib.Duck32.encode("d1", spec.getBytes());

    int colon = addr.indexOf(":");
    String without = addr.substring(colon+1);
    String wrong = "d2:" + without;

    snowblossomlib.Duck32.decode("d2", wrong);
  }

  @Test
  public void testAddressChecksumDataChange()
    throws Exception
  {
    Random rnd = new Random();
    byte[] buff = new byte[Globals.ADDRESS_SPEC_HASH_LEN];
    int checks =0;

    for(int pass=0; pass<10000; pass++)
    {
      rnd.nextBytes(buff);
      snowblossomlib.AddressSpecHash spec = new AddressSpecHash(buff);

      String addr = snowblossomlib.Duck32.encode("d1", spec.getBytes());

      int colon = addr.indexOf(":");
      String without = addr.substring(colon+1);

      int idx = rnd.nextInt(without.length());
      char replace = snowblossomlib.Duck32.CHARSET.charAt(rnd.nextInt(32) );

      String n = without.substring(0, idx) + replace + without.substring(idx+1);

      Assert.assertEquals(without.length(), n.length());

      if (!without.equals(n))
      {
        System.out.println(without + " " + n);
        try
        {
          checks++;
          Duck32.decode("d1", n);
          Assert.fail("Should have gotten exception");
        }
        catch(ValidationException e){}
      }
    }
    System.out.println("Did " + checks + " checksum mutations");
    Assert.assertTrue(checks > 8000);
  }



 
}
