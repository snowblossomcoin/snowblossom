package snowblossom.iceleaf;

import java.io.File;

public class SystemUtil
{
  public static File getImportantDataDirectory()
  {
    String appdata =  System.getenv("APPDATA");
    if (appdata!=null) // probably windows
    {
      File f = new File(appdata);
      return new File(f, "snowblossom");
    }

    String home = System.getProperties().getProperty("user.home");

    File home_file = new File(home);
    return new File(home_file, ".snowblossom");
  }

  public static File getNodeDataDirectory()
  {
    String appdata =  System.getenv("LOCALAPPDATA");
    if (appdata!=null) // probably windows
    {
      File f = new File(appdata);
      return new File(f, "snowblossom-node");
    }

    String home = System.getProperties().getProperty("user.home");

    File home_file = new File(home);
    return new File(home_file, ".snowblossom-node");
  }


  public static void main(String args[]) throws Exception
  {
    System.out.println("Appdata: " + System.getenv("APPDATA"));
    System.out.println("LocalAppdata: " + System.getenv("LOCALAPPDATA"));
    System.out.println("Home: " + System.getProperties().getProperty("user.home"));
    System.out.println("OS: " + System.getProperties().getProperty("os.name"));
    System.out.println("OS: " + System.getProperties().getProperty("os.arch"));
    System.out.println("Important Data: " + getImportantDataDirectory());
    System.out.println("Node Data: " + getNodeDataDirectory());

  }

}
