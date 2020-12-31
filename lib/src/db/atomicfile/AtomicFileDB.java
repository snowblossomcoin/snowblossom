package snowblossom.lib.db.atomicfile;

import duckutil.Config;
import java.io.File;
import java.util.logging.Logger;
import snowblossom.lib.db.DBMap;
import snowblossom.lib.db.DBMapMutationSet;
import snowblossom.lib.db.DBProvider;

public class AtomicFileDB extends DBProvider
{
  private final File base;

  public AtomicFileDB(Config config)
    throws Exception
  {
    super(config);
    config.require("db_path");

    String path = config.get("db_path");

    base = new File(path);
    base.mkdirs();

    logger.info(String.format("Loading AtomicFileDB with path %s", path));

  }

  @Override
  public DBMapMutationSet openMutationMapSet(String name) throws Exception
  {
    return new AtomicFileMapSet(new File(base, name));
  }

  @Override
  public DBMap openMap(String name) throws Exception
  {
    return new AtomicFileMap(new File(base, name));
  }

}
