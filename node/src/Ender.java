package snowblossom.node;

import java.util.Random;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Written to try to figure out an issue.  Should not be used in production.
 */
public class Ender extends Thread
{
  private static final Logger logger = Logger.getLogger("snowblossom.node");
  final SnowBlossomNode node;

  public Ender(SnowBlossomNode node)
  {
    this.node = node;
    setName("Ender");
    setDaemon(true);

  }

  @Override
  public void run()
  {
    Random rnd = new Random();
    try
    {

    long sleep_time_sec = rnd.nextInt(3600);

    this.sleep(sleep_time_sec * 1000L);
    logger.warning("Ender shutdown");
    System.exit(0);
    }
    catch(Throwable e)
    {
      logger.warning(e.toString());
      e.printStackTrace();
    }

  }
}
