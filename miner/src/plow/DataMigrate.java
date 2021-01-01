package snowblossom.miner.plow;

import duckutil.Config;
import duckutil.ConfigMem;
import snowblossom.lib.db.DB;
import snowblossom.lib.db.lobstack.LobstackDB;
import snowblossom.lib.db.rocksdb.JRocksDB;
import snowblossom.lib.db.atomicfile.AtomicFileDB;
import java.util.TreeMap;

import snowblossom.mining.proto.*;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import snowblossom.lib.Globals;
import com.google.protobuf.ByteString;


/**
 * This tool is to migrate data from a rocksb 
 */
public class DataMigrate
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  public static void main(String args[])
    throws Exception
  {
    if (args.length != 2)
    {
      System.out.println("Expected parameters:");
      System.out.println("DataMigrate <rocksdb_path> <atomic_file_path>");
      
      System.exit(1);
    }

    Globals.addCryptoProvider();
    String rocksdb_path = args[0];
    String atomic_file_path = args[1];

    System.out.println("Migrating data from rocksdb: " + rocksdb_path);
    System.out.println("Migrating data to atomic file db: " + atomic_file_path);


    DB src = null;
    DB dst = null;

    {
      TreeMap<String, String> config_map = new TreeMap<>();
      config_map.put("db_path", rocksdb_path);
      Config config = new ConfigMem(config_map);
      src = new DB(config, new JRocksDB(config));
    }

    {
      TreeMap<String, String> config_map = new TreeMap<>();
      config_map.put("db_path", atomic_file_path);
      Config config = new ConfigMem(config_map);
      dst = new DB(config, new AtomicFileDB(config));
    }

    if (src.getSpecialMap().get(MrPlow.PPLNS_STATE_KEY)==null)
    {
      logger.severe("Source does not have PPLNS State.  Aborting");
      System.exit(1);
    }
    if (dst.getSpecialMap().get(MrPlow.PPLNS_STATE_KEY)!=null)
    {
      logger.severe("Destination already has PPLNS State.  Aborting");
      System.exit(1);
    }

    PPLNSState pplns_state = PPLNSState.parseFrom(src.getSpecialMap().get(MrPlow.PPLNS_STATE_KEY));

    logger.info(String.format("Have source PPLNS state with %d entries",pplns_state.getShareEntriesCount()));


    List<ByteString> lst = src.getSpecialMapSet().getSet(MrPlow.BLOCK_KEY, 100000);

    for(ByteString bs : lst)
    {
      dst.getSpecialMapSet().add(MrPlow.BLOCK_KEY, bs);
    }
    logger.info("Saved found block list: " + lst.size());


    // Saving state

    dst.getSpecialMap().put(MrPlow.PPLNS_STATE_KEY, pplns_state.toByteString());
    logger.info("PPLNS state saved");
    logger.info("Migration complete");



  }

}
