package snowblossom.lib;

import duckutil.Config;

import java.io.FileInputStream;
import java.util.Enumeration;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class LogSetup
{
  public static void setup(Config config)
    throws java.io.IOException
  {

    if (config.isSet("log_config_file"))
    {
      try
      {
        LogManager.getLogManager().readConfiguration(new FileInputStream(config.get("log_config_file")));
      }
      catch (Exception e)
      {
        System.out.println("FAILED TO INITIALIZE LOGGING: " + e);
      }
    }
    //LogManager.getLogManager().getLogger(Logger.GLOBAL_LOGGER_NAME).setLevel(Level.WARNING);
    //LogManager.getLogManager().getLogger("").setLevel(Level.WARNING);
  
    //listLoggers();
  }

  public static void listLoggers()
  {

    Enumeration<String> e =  LogManager.getLogManager().getLoggerNames();
    while(e.hasMoreElements())
    {
      String s =e.nextElement();

      System.out.println("Logger: " + s);
      Logger m = LogManager.getLogManager().getLogger(s);
      if (m != null)
      {
        Logger p = m.getParent();
        if (p != null) System.out.println(" p: " + p.getName());
        for(Handler h : m.getHandlers())
        {
          System.out.println("  " + h.getClass().getName());
        }
      }
    }
  }

}
