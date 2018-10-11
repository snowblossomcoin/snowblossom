package snowblossom.lib;

import java.util.logging.Level;
import java.util.logging.Logger;
import duckutil.NetUtil;


public class TimeWatcher extends Thread
{
  private static final Logger logger = Logger.getLogger("snowblossom.node");

  public TimeWatcher()
  {
    setDaemon(true);
    setName("TimeWatcher");
  }

  public void run()
  {
    while(true)
    {
      try
      {
        checkTime();

      }
      catch(Throwable t)
      {
        logger.info("Exception in TimeWatcher: " + t);
      }
      try
      {
        sleep(300000);
      }
      catch(Throwable t)
      {
        logger.info("Exception in TimeWatcher: " + t);
      }
    }
  }


  private void checkTime()
    throws Exception
  {
    long start = System.currentTimeMillis();

    long server = Long.parseLong(NetUtil.getUrlLine("https://timecheck.snowblossom.org/time"));

    long end = System.currentTimeMillis();

    long mid = (end + start) /2;

    long diff = Math.abs(server - mid);
    if (diff > Globals.CLOCK_SKEW_WARN_MS)
    {
      logger.log(Level.WARNING, String.format("Local clock seems to be off by %d ms", diff));
    }
    logger.log(Level.FINE, String.format("Local clock seems to be off by %d ms", diff));
    
  }



}
