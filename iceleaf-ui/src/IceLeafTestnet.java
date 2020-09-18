package snowblossom.iceleaf;

import java.util.prefs.Preferences;
import snowblossom.lib.Globals;
import snowblossom.lib.NetworkParamsTestnet;

public class IceLeafTestnet
{
  public static void main(String args[])
    throws Exception
  {
    Globals.addCryptoProvider();

    Preferences ice_leaf_prefs = Preferences.userNodeForPackage(new NetworkParamsTestnet().getClass());
    new IceLeaf(new NetworkParamsTestnet(), ice_leaf_prefs);
    
  }


  public IceLeafTestnet()
  {

  }

}
