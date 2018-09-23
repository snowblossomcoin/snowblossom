package snowblossom.lib.db.lobstack;

import duckutil.Config;
import snowblossom.lib.db.DBProvider;
import snowblossom.lib.db.DBMap;
import snowblossom.lib.db.DBMapMutationSet;
import lobstack.Lobstack;

import java.io.File;
import java.util.logging.Logger;


public class LobstackDB extends DBProvider
{
  private static final Logger logger = Logger.getLogger("snowblossom.db");

	private Lobstack stack;

  public LobstackDB(Config config)
		throws Exception
  {
    super(config);
    config.require("db_path");

    String path = config.get("db_path");

    new File(path).mkdirs();

    logger.info(String.format("Loadng LobstackDB with path %s", path));

		stack = new Lobstack(new File(path), "snowdb");

  }

  @Override
  public DBMapMutationSet openMutationMapSet(String name) throws Exception
  {
		throw new Exception("NOT IMPLEMENTED");
  }

  @Override
  public DBMap openMap(String name) throws Exception
  {
    return new LobstackDBMap(stack, name);
  }

}
