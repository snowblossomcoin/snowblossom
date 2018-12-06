package snowblossom.client;

import snowblossom.lib.*;
import snowblossom.proto.*;

import duckutil.MultiAtomicLong;
import java.text.DecimalFormat;
import com.google.protobuf.util.JsonFormat;

/**
 * CREATES A USELESS UNSPENDABLE ADDRESS.  ONLY SEND FUNDS
 * TO THE RESULTING ADDRESS IF YOU DON'T WANT ANYONE TO BE ABLE
 * TO SPEND THEM EVER. LIKE OMG WTF BBQ.
 */
public class HoleGen
{
  public static void main(String args[]) throws Exception
  {
    Globals.addCryptoProvider();

    String find = args[0];
    new HoleGen(find);
  }

  protected final NetworkParams params;


  public HoleGen(String find)
    throws Exception
  {

    params = new NetworkParamsProd();


    System.out.println( Duck32.mangleString(params.getAddressPrefix(), find));

    
  }

}

