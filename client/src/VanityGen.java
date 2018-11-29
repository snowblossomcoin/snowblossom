package snowblossom.client;

import snowblossom.lib.*;
import snowblossom.proto.*;

import duckutil.MultiAtomicLong;
import java.text.DecimalFormat;
import com.google.protobuf.util.JsonFormat;

public class VanityGen
{
  public static void main(String args[]) throws Exception
  {
    Globals.addCryptoProvider();

    String find = args[0];
    boolean st = Boolean.parseBoolean(args[1]);
    boolean end = Boolean.parseBoolean(args[2]);
    new VanityGen(find, st, end);
  }

  protected final String find;
  protected final boolean starts;
  protected final boolean ends;
  protected final NetworkParams params;

  protected volatile boolean done;
  protected MultiAtomicLong counter;
  protected WalletKeyPair found_wkp;

  public VanityGen(String find, boolean starts, boolean ends)
    throws Exception
  {
    this.find = find;
    this.starts = starts;
    this.ends = ends;
    counter = new MultiAtomicLong();

    params = new NetworkParamsProd();

    for(int i=0; i<32; i++)
    {
      new WorkerThread().start();
    }

    long last = 0;
    while(!done)
    {
      Thread.sleep(5000);
      long n = counter.sum();
      double diff = n - last;
      double rate = diff / 5.0;
      DecimalFormat df = new DecimalFormat("0.0");
      System.out.println(String.format("Checked %d at rate %s", n, df.format(rate)));


      last = n;
    }
    WalletKeyPair wkp = found_wkp;
    WalletDatabase.Builder wallet_builder = WalletDatabase.newBuilder();
    wallet_builder.addKeys(wkp);
    AddressSpec claim = AddressUtil.getSimpleSpecForKey(wkp);
    wallet_builder.addAddresses(claim);
    String address = AddressUtil.getAddressString(claim, params);
    wallet_builder.putUsedAddresses(address, true);

    JsonFormat.Printer printer = JsonFormat.printer();
    System.out.println(printer.print(wallet_builder.build()));


    
  }


  public class WorkerThread extends Thread
  {
    public WorkerThread()
    {
      setDaemon(true);
      setName("VanityGen:WorkerThread");
      setPriority(3);
    }

    public void run()
    {
      String starter=null;
      if (starts)
      {
        starter=params.getAddressPrefix() + ":" + find;
      }
      while(!done)
      {
        WalletKeyPair wkp = KeyUtil.generateWalletStandardECKey();
        AddressSpec claim = AddressUtil.getSimpleSpecForKey(wkp);
        String address = AddressUtil.getAddressString(claim, params); 

        counter.add(1L);

        if (ends)
        {
          if (address.endsWith(find))
          {
            System.out.println(address);
            found_wkp=wkp;
            done=true;
          }
        }
        else if (starter!= null)
        {
          if (address.startsWith(starter))
          {
            System.out.println(address);
            found_wkp=wkp;
            done=true;
          }
        }
        else if (address.contains(find))
        {
            System.out.println(address);
            found_wkp=wkp;
            done=true;
        
        }

      }

    }

  }

}

