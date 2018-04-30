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

public class KeyUtil
{
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
