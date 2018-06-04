package snowblossom.lib;

import java.security.Provider;
import java.security.Security;

/**
 * Prints all algorithms from all providers just to see what we have
 */
public class ShowAlgo
{

  public static void main(String args[])
  {
    Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

    Provider[] p_a = Security.getProviders();
    for(Provider p : p_a)
    {
      System.out.println("Provider: " + p.toString());
      System.out.println("-----------------------------------");
      for(Provider.Service ps : p.getServices())
      {
        System.out.println("  " + ps.toString());
      }
    }
  }
}

