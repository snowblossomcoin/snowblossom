package snowblossom;

import java.util.logging.Logger;
import java.util.logging.LogManager;
import java.util.logging.Level;
import java.util.Enumeration;
import java.util.logging.Handler;
import java.io.FileInputStream;

import duckutil.Config;

public class LogSetup
{
  public static void setup(Config config)
    throws java.io.IOException
  {

    if (config.isSet("log_config_file"))
    {
      LogManager.getLogManager().readConfiguration(new FileInputStream(config.get("log_config_file")));

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
