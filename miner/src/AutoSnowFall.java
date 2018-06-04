package snowblossom.miner;

import com.google.protobuf.ByteString;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.SnowFall;
import snowblossom.lib.SnowFieldInfo;
import snowblossom.lib.SnowMerkle;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;


public class AutoSnowFall extends Thread
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");
  
  private boolean done=false;

  private File snow_path;
  private NetworkParams params;
  private int field;

  public AutoSnowFall(File snow_path, NetworkParams params, int field)
  {
    this.snow_path = snow_path;
    this.params = params;
    this.field = field;
    
  }

  public void run()
  {
    try
    {
    SnowFieldInfo field_info = params.getSnowFieldInfo(field);
    logger.info(String.format("Started automatic snowfall of field %d - %s", field, field_info.getName()));

    String path_name = params.getFieldSeeds().get(field);

    File field_dir = new File(snow_path, path_name);
    File snow_file = new File(field_dir, path_name +".snow");

    field_dir.mkdirs();

    new SnowFall(snow_file.getPath(), path_name, field_info.getLength());

    logger.info(String.format("Snow field written: %s", snow_file.getPath()));
    logger.info("Starting merkle deck files");

    SnowMerkle merk = new SnowMerkle(field_dir, path_name, true);

    ByteString found_root = merk.getRootHash();

    if (field_info.getMerkleRootHash().equals(found_root))
    {
      logger.info(String.format("Field %d - %s successfully matches hash %s", field, field_info.getName(), merk.getRootHashStr()));
    }
    else
    {
      logger.info("Hash mismatch for field");
    }

    done=true;

    }
    catch(Exception e)
    {
      logger.log(Level.WARNING, "Exception in autosnow: " + e);
    }
    

  }

  public boolean isDone()
  {
    return done;
  }

}
