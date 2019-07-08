package snowblossom.iceleaf;

import java.io.File;
import snowblossom.lib.NetworkParams;

public class SystemUtil
{
  public static File getImportantDataDirectory(NetworkParams params)
  {
    String name = params.getNetworkName();
    if (!name.equals("snowblossom"))
    {
      name = "snowblossom-" + name;
    }


    String appdata =  System.getenv("APPDATA");
    if (appdata!=null) // probably windows
    {
      File f = new File(appdata);
      return new File(f, name);
    }

    String home = System.getProperties().getProperty("user.home");

    File home_file = new File(home);
    return new File(home_file, "." + name);
  }

  public static File getNodeDataDirectory(NetworkParams params)
  {
    String name = params.getNetworkName();
    if (!name.equals("snowblossom"))
    {
      name = "snowblossom-" + name;
    }

    String appdata =  System.getenv("LOCALAPPDATA");
    if (appdata!=null) // probably windows
    {
      File f = new File(appdata);
      return new File(f, name + "-node");
    }

    String home = System.getProperties().getProperty("user.home");

    File home_file = new File(home);
    return new File(home_file, String.format(".%s-node", name));
  }


  public static void main(String args[]) throws Exception
  {
    System.out.println("Appdata: " + System.getenv("APPDATA"));
    System.out.println("LocalAppdata: " + System.getenv("LOCALAPPDATA"));
    System.out.println("Home: " + System.getProperties().getProperty("user.home"));
    System.out.println("OS: " + System.getProperties().getProperty("os.name"));
    System.out.println("OS: " + System.getProperties().getProperty("os.arch"));

  }

}
