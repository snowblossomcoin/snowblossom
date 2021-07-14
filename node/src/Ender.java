package snowblossom.node;

import java.util.Random;

import java.util.logging.Level;
import java.util.logging.Logger;
import duckutil.PeriodicThread;


/**
 * Written to try to figure out an issue.  Should not be used in production.
 */
public class Ender extends PeriodicThread
{
  private static final Logger logger = Logger.getLogger("snowblossom.node");
  final SnowBlossomNode node;
  
  boolean first=true;

  public Ender(SnowBlossomNode node)
  {
    super(60L * 60L * 1000L, 15.0 * 60.0 * 1000.0);

    this.node = node;
    setName("Ender");
    setDaemon(true);

  }

  @Override
  public void runPass()
    throws Exception
  {
    if (first)
    {
      first=false;
      return;
    }
    Random rnd = new Random();
    try
    {
      logger.warning("Ender shutdown");
      //System.exit(0);
      node.getPeerage().closeAll();
    }
    catch(Throwable e)
    {
      logger.warning(e.toString());
      e.printStackTrace();
    }

  }
}
