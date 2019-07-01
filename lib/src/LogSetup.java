package snowblossom.lib;

import duckutil.Config;

import java.io.FileInputStream;
import java.util.Enumeration;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Properties;

public class LogSetup
{
  private static final Logger logger = Logger.getLogger("snowblossom.logsetup");

  private static Properties log_props = new Properties();

  public static void setup(Config config)
    throws java.io.IOException
  {

    if (config.isSet("log_config_file"))
    {
      try
      {
        log_props.load(new FileInputStream(config.get("log_config_file")));
        LogManager.getLogManager().readConfiguration(new FileInputStream(config.get("log_config_file")));
      }
      catch (Exception e)
      {
        System.out.println("FAILED TO INITIALIZE LOGGING: " + e);
      }
      
    }
    else
    {
      log_props.setProperty(".level", "FINE");
      log_props.setProperty("io.level", "SEVERE");
    }

    

  
    //listLoggers();
  }

  public static void fixLevels()
  {
    LogManager lm = LogManager.getLogManager();
    Enumeration<String> e =  LogManager.getLogManager().getLoggerNames();
    while(e.hasMoreElements())
    {
      String s = e.nextElement();
      Logger m = LogManager.getLogManager().getLogger(s);
      if (m != null)
      {
        if (m.getLevel() == null)
        {
          String name = s;
          while(name.length() > 0)
          {
            if (log_props.getProperty(name +".level") != null)
            {
              Level lvl = Level.parse(log_props.getProperty(name +".level"));
              logger.fine(String.format("Setting level for %s to %s based on %s", s, lvl, name));
              m.setLevel(lvl);
              break;
            }
            else
            {
              int last_dot = name.lastIndexOf('.');
              if (last_dot < 0)
              {
                break;
              }
              else
              {
                name = name.substring(0, last_dot);
              }

            }

          }

        }

      }

    }

  }

  public static void listLoggers()
  {

    Enumeration<String> e =  LogManager.getLogManager().getLoggerNames();
    while(e.hasMoreElements())
    {
      String s =e.nextElement();

      System.out.print("Logger: " + s + " ");
      Logger m = LogManager.getLogManager().getLogger(s);
      if (m != null)
      {
        System.out.println(m.getLevel());
        //m.setLevel(Level.WARNING);
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
