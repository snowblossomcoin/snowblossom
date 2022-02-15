package snowblossom.lib.db.rocksdb;

import com.google.common.collect.ImmutableList;
import duckutil.Config;
import duckutil.PeriodicThread;
import java.io.File;
import java.util.TreeMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.rocksdb.FlushOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteOptions;
import snowblossom.lib.db.DBMap;
import snowblossom.lib.db.DBMapMutationSet;
import snowblossom.lib.db.DBProvider;

public class JRocksDB extends DBProvider
{
  private static final Logger logger = Logger.getLogger("snowblossom.db");

  private RocksDB shared_db;
  private Options options;
  private boolean use_separate_dbs;

  private TreeMap<String, RocksDB> separate_db_map;

  private WriteOptions sharedWriteOptions;

  private File base_path;

  public JRocksDB(Config config)
    throws Exception
  {
    super(config);

    use_separate_dbs=config.getBoolean("db_separate");
    
    config.require("db_path");
    
    String path = config.get("db_path");

    base_path = new File(path);

    base_path.mkdirs();

    logger.info(String.format("Loading RocksDB with path %s", path));

    RocksDB.loadLibrary();
    sharedWriteOptions = new WriteOptions();
    sharedWriteOptions.setDisableWAL(false);
    sharedWriteOptions.setSync(false);


    // Separate DBs should only be used when you don't care about syncing between
    // the databases,  If you are fine with writes to them being preserved out of order
    // relative to each other it should be fine.
    // For example, in combined DBs if you write a to A then b to B, you will either get {}, {a}, or {a,b} 
    // on a bad shutdown.  If you use separate, you could very well get {b}.

    if (use_separate_dbs)
    {
      separate_db_map = new TreeMap<>();
    }
    else
    {
      shared_db = openRocksDB(path);
    }

  }

  protected RocksDB openRocksDB(String path)
    throws Exception
  {

    Options options = new Options();

    options.setIncreaseParallelism(16);
    options.setCreateIfMissing(true);
    options.setAllowMmapReads(true);
    options.setKeepLogFileNum(5);
    //options.setAllowMmapWrites(true);

    return RocksDB.open(options, path);
  }

  protected WriteOptions getWriteOption()
  {
    return sharedWriteOptions;
  }

  @Override
  public synchronized DBMapMutationSet openMutationMapSet(String name) throws Exception
  {
    RocksDB db = null;
    if (use_separate_dbs)
    {
      if (separate_db_map.containsKey(name)) db = separate_db_map.get(name);
      else
      {
        File p = new File(base_path, name);
        p.mkdirs();
        db = openRocksDB(p.getPath());
        separate_db_map.put(name, db);
      }
    }
    else
    {
      db = shared_db;
    }

    return new RocksDBMapMutationSet(this, db, name);
  }

  @Override
  public synchronized DBMap openMap(String name) throws Exception
  {
    RocksDB db = null;
    if (use_separate_dbs)
    {
      if (separate_db_map.containsKey(name)) db = separate_db_map.get(name);
      else
      {
        File p = new File(base_path, name);
        p.mkdirs();
        db = openRocksDB(p.getPath());
        separate_db_map.put(name, db);
      }
    }
    else
    {
      db = shared_db;
    }

    return new RocksDBMap(this, db, name);
  }

  private synchronized List<RocksDB> getDBList()
  {
    if (!use_separate_dbs)
    {
      return ImmutableList.of(shared_db);
    }
    return ImmutableList.copyOf(separate_db_map.values());
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
      if (shared_db != null)
      {
        shared_db.flush(fl);
      }
      if (separate_db_map != null)
      {
        for(RocksDB db : separate_db_map.values())
        {
          db.flush(fl);
        }
      }
    }
    catch(Exception e)
    {
      logger.log(Level.WARNING, "rocks flush", e);
    }

    logger.info("RocksDB flush completed");

  }

  @Override
  public void interactiveMaint() throws Exception
  {
    logger.info("Compaction started");
    long t1 = System.currentTimeMillis();
    for(RocksDB db : getDBList())
    {
      db.compactRange();
    }
    long t2 = System.currentTimeMillis();
    double sec = (t2 - t1) / 1000.0;
    logger.info("Compaction run in " + sec + " seconds");
  }

}
