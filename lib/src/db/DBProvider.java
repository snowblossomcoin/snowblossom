package snowblossom.lib.db;

import duckutil.Config;
import java.util.logging.Logger;

public abstract class DBProvider
{
  protected static final Logger logger = Logger.getLogger("snowblossom.db");

  protected Config config;

  public DBProvider(Config config)
  {
  }


  public void close()
  {
  }

  public abstract DBMap openMap(String name) throws Exception;
  public abstract DBMapMutationSet openMutationMapSet(String name) throws Exception;

  /**
   * Do any maintenance or checks or tuning or cleanup that is helpful to run
   * infrequently but does not make the database not usable by other threads.
   */
  public void interactiveMaint() throws Exception
  {}

}
