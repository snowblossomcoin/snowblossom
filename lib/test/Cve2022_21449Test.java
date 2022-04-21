package lib.test;

import com.google.protobuf.ByteString;
import java.math.BigInteger;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;
import snowblossom.lib.BlockchainUtil;
import org.bouncycastle.asn1.*;

import java.security.*;
// Test code thanks to https://neilmadden.blog/2022/04/19/psychic-signatures-in-java/
// conversion code thanks to https://stackoverflow.com/questions/61860104/converting-p1363-format-to-asn-1-der-format-using-java


public class Cve2022_21449Test
{

  public static byte[] getBlankSig()
    throws Exception
  {
    BigInteger r = BigInteger.ZERO;
    BigInteger s = BigInteger.ZERO;

    ASN1EncodableVector v = new ASN1EncodableVector();
    v.add(new ASN1Integer(r)); v.add(new ASN1Integer(s));

    return new DERSequence(v) .getEncoded();
  }

  @Test
  public void testVulnBC()
    throws Exception
  {
    snowblossom.lib.Globals.addCryptoProvider();
    KeyPair keys = KeyPairGenerator.getInstance("EC").generateKeyPair();
    Signature sig = Signature.getInstance("SHA256WithECDSA","BC");
    sig.initVerify(keys.getPublic());
    sig.update("Hello, World".getBytes());
    Assert.assertFalse(sig.verify(getBlankSig()));

  }
  

}
