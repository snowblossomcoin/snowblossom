package snowblossom;

import com.google.protobuf.ByteString;

import org.bouncycastle.asn1.ASN1StreamParser;
import org.bouncycastle.asn1.DERSequenceParser;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;


import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.apache.commons.codec.binary.Hex;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;

import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;

import org.junit.Assert;


public class KeyUtil
{
  public static ByteString EC_SECP256K1_PREFIX= HexUtil.stringToHex("3036301006072a8648ce3d020106052b8104000a032200");

  public static PublicKey convertCompressedECDSA(ByteString encoded)
    throws ValidationException
  {
    if (encoded.size()!=33)
    {
      throw new ValidationException("Compressed key must be exactly 33 bytes");
    }

    ByteString fullkey = EC_SECP256K1_PREFIX.concat(encoded);

    return decodeKey(fullkey, "ECDSA");

  }

  public static ByteString getCompressedPublicKeyEncoding(PublicKey key)
  {
    ByteString full = ByteString.copyFrom(key.getEncoded());
    Assert.assertTrue(full.startsWith(EC_SECP256K1_PREFIX));
    Assert.assertEquals( full.size(), EC_SECP256K1_PREFIX.size() + 33);

    return full.substring(EC_SECP256K1_PREFIX.size());
  }

  public static PublicKey decodeKey(ByteString encoded, String algo)
    throws ValidationException
  {
    try
    {
      X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded.toByteArray());
      KeyFactory fact = KeyFactory.getInstance(algo);
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
      KeyFactory fact = KeyFactory.getInstance(algo);
      return fact.generatePrivate(spec);
    }
    catch(java.security.GeneralSecurityException e)
    {
      throw new ValidationException("Error decoding key", e);
    }

  }

  public static KeyPair generateECCompressedKey()
  {
    try
    {
      ECGenParameterSpec spec = new ECGenParameterSpec("secp256k1");
      KeyPairGenerator key_gen = KeyPairGenerator.getInstance("ECDSA", "BC");
      key_gen.initialize(spec);

      KeyPair pair = key_gen.genKeyPair();

      BCECPublicKey pub = (BCECPublicKey) pair.getPublic();

      pub.setPointFormat("COMPRESSED");

      return pair;
    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  public static ArrayList<String> extractObjectIdentifiers(ByteString encoded)
    throws ValidationException
  {
    try
    {
      ArrayList<String> answers = new ArrayList<>();
      ASN1StreamParser parser = new ASN1StreamParser(encoded.toByteArray());

      while(true)
      {
        ASN1Encodable encodable = parser.readObject();
        if (encodable == null) break;

        extractOID(encodable, answers);
      }
      return answers;
    }
    catch(java.io.IOException e)
    {
      throw new ValidationException("OID extraction failed for key", e);
    }
  }

  private static void extractOID(ASN1Encodable input, ArrayList<String> answers)
    throws java.io.IOException
  {
    if (input instanceof DERSequenceParser)
    {
      DERSequenceParser parser = (DERSequenceParser) input;
      while(true)
      {
        ASN1Encodable encodable = parser.readObject();
        if (encodable == null) break;
        extractOID(encodable, answers);
      }

    }
    else if (input instanceof ASN1ObjectIdentifier)
    {
      ASN1ObjectIdentifier id = (ASN1ObjectIdentifier) input;
      answers.add(id.getId());
    }

  }

  public static String decomposeASN1Encoded(byte[] input)
    throws Exception
  {
    return decomposeASN1Encoded(ByteString.copyFrom(input));
  }
  /**
   * Produce a string that is a decomposition of the given x.509 or ASN1 encoded
   * object.  For learning and debugging purposes.
   */
  public static String decomposeASN1Encoded(ByteString input)
    throws Exception
  {
    ByteArrayOutputStream byte_out = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(byte_out);

    ASN1StreamParser parser = new ASN1StreamParser(input.toByteArray());
    out.println("ASN1StreamParser");

    while(true)
    {
      ASN1Encodable encodable = parser.readObject();
      if (encodable == null) break;

      decomposeEncodable(encodable, 2, out);
    }

    out.flush();
    return new String(byte_out.toByteArray());

  }

  private static void decomposeEncodable(ASN1Encodable input, int indent, PrintStream out)
    throws Exception
  {
    printdent(out, indent);
    out.println(input.getClass());
    if (input instanceof DERSequenceParser)
    {
      DERSequenceParser parser = (DERSequenceParser) input;
      while(true)
      {
        ASN1Encodable encodable = parser.readObject();
        if (encodable == null) break;
        decomposeEncodable(encodable, indent+2, out);
      }

    }
    else if (input instanceof ASN1ObjectIdentifier)
    {
      ASN1ObjectIdentifier id = (ASN1ObjectIdentifier) input;
      printdent(out, indent+2);
      out.println("ID: " + id.getId());
    }
    else if ((input instanceof ASN1Integer) || (input instanceof DERBitString))
    {
      printdent(out, indent+2);
      out.println("Value: " + input);
    }
    else
    {
      printdent(out, indent+2);
      out.println("Don't know what to do with this");
    }

  }
  private static void printdent(PrintStream out, int indent)
  {
    for(int i=0; i<indent; i++) out.print(' ');
  }

}
