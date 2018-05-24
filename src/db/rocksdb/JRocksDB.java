package snowblossom.db.rocksdb;

import duckutil.Config;

import snowblossom.db.DB;
import snowblossom.db.DBMap;
import snowblossom.db.DBMapMutationSet;

import org.rocksdb.RocksDB;
import org.rocksdb.Options;
import org.rocksdb.WriteOptions;
import org.rocksdb.FlushOptions;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.File;



public class JRocksDB extends DB
{
  private static final Logger logger = Logger.getLogger("snowblossom.db");

  private RocksDB db;
  private Options options;

  private WriteOptions sharedWriteOptions;

  public JRocksDB(Config config)
    throws Exception
  {
    super(config);

    config.require("db_path");

    String path = config.get("db_path");

    new File(path).mkdirs();

    logger.info(String.format("Loadng RocksDB with path %s", path));

    RocksDB.loadLibrary();
    Options options = new Options();

    options.setIncreaseParallelism(16);
    options.setCreateIfMissing(true);
    options.setAllowMmapReads(true);
    //options.setAllowMmapWrites(true);

    sharedWriteOptions = new WriteOptions();
    sharedWriteOptions.setDisableWAL(false);
    sharedWriteOptions.setSync(false);

    db = RocksDB.open(options, path);

    open();
  }

  protected WriteOptions getWriteOption()
  {
    return sharedWriteOptions;
  }

  @Override
  protected DBMapMutationSet openMutationMapSet(String name) throws Exception
  {
    return new RocksDBMapMutationSet(this, db, name);
  }

  @Override
  protected DBMap openMap(String name) throws Exception
  {
    return new RocksDBMap(this, db, name);
  }

  @Override
  public void close()
  {
    super.close();

    logger.info("RocksDB flush started");
    try
    {
      FlushOptions fl = new FlushOptions();
      fl.setWaitForFlush(true);
      db.flush(fl);
    }
    catch(Exception e)
    {
      logger.log(Level.WARNING, "rocks flush", e);
    }

    logger.info("RocksDB flush completed");

  }

}
