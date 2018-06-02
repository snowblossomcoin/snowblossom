package snowblossomlib.db.lobstack;

import duckutil.Config;
import lobstack.Lobstack;
import snowblossomlib.db.DB;
import snowblossomlib.db.DBMap;
import snowblossomlib.db.DBMapMutationSet;

import java.io.File;
import java.util.logging.Logger;


public class LobstackDB extends DB
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

   	open(); 
  }

  @Override
  protected DBMapMutationSet openMutationMapSet(String name) throws Exception
  {
		throw new Exception("NOT IMPLEMENTED");
  }

  @Override
  protected DBMap openMap(String name) throws Exception
  {
    return new LobstackDBMap(stack, name);
  }

}
