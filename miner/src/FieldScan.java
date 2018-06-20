package snowblossom.miner;

import com.google.common.collect.ImmutableSortedMap;
import duckutil.Config;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.SnowFieldInfo;
import snowblossom.lib.Validation;
import snowblossom.proto.SnowPowProof;

import java.io.File;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.logging.Logger;

public class FieldScan
{

  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  private File path;
  private NetworkParams params;

  private ImmutableSortedMap<Integer, SnowMerkleProof> availible_fields;

  private volatile AutoSnowFall auto_snow;
  private Config config;
  private final double precacheGig;

  public FieldScan(File path, NetworkParams params, Config config)
  {
    this.path = path;
    this.params = params;
    this.config = config;
    precacheGig = config.getDoubleWithDefault("memfield_precache_gb", 0);
    scan(true);
  }


  public void scan(boolean report)
  {
    TreeMap<Integer, SnowMerkleProof> fmap = new TreeMap<>();

    for(Map.Entry<Integer, String> me : params.getFieldSeeds().entrySet())
    {
      int field_number = me.getKey();
      SnowFieldInfo info = params.getSnowFieldInfo(field_number);
      String name = me.getValue();
      File field_folder = new File(path, name);
      if (field_folder.exists() && field_folder.isDirectory())
      {
        try
        {
          boolean memfield = config.getBoolean("memfield");
          long precache = 0;
          if (precacheGig > 0.01)
          {
            memfield = false;
            precache = (long)(precacheGig * 1024.0 * 1024.0 * 1024.0);
          }
          logger.info("creating field: " + field_folder + " memfield=" + memfield + ", precache=" + precache);
          SnowMerkleProof proof = new SnowMerkleProof(field_folder, name, memfield, precache);

          for(int i = 0; i<16; i++)
          {
            checkField(field_number, proof);
          }

          fmap.put(field_number, proof);
          logger.info(String.format("Snow field %d %s (%s) is working", field_number,
            name,
            info.getName()));

        }
        catch(Throwable e)
        {
          logger.info(String.format("Unable to load %s, error: %s", name, e.toString()));
        }
      }
      else
      {
        logger.info(String.format("Not loading %d %s (%s), directory not present: %s",
          field_number,
          name,
          info.getName(),
          field_folder));
      }
    }

    availible_fields = ImmutableSortedMap.copyOf(fmap);

  }

  private void checkField(int field_number, SnowMerkleProof proof)
    throws java.io.IOException
  {
    SplittableRandom rnd = new SplittableRandom();

    long max = proof.getTotalWords();

    long check = rnd.nextLong(max);
    SnowPowProof p = proof.getProof(check);

    SnowFieldInfo info = params.getSnowFieldInfo(field_number);

    if (!Validation.checkProof(p, info.getMerkleRootHash(), info.getLength() ))
    {
      throw new RuntimeException("proof check failed");
    }
  }

  public boolean isPreCaching()
  {
    if (precacheGig<= 0.001) { return false;}
    for (SnowMerkleProof field : availible_fields.values())
    {
      if (field.isPreCaching()) return true;
    }
    return false;
  }

  public synchronized int selectField(int min_field)
  {
    if (auto_snow != null)
    {
      if(auto_snow.isDone())
      {
        auto_snow=null;
        scan(true);
      }
    }

    Integer i = availible_fields.ceilingKey(min_field);
    if (i == null)
    {
      if (config.isSet("auto_snow") && config.getBoolean("auto_snow"))
      {
        if (auto_snow == null)
        {
          auto_snow = new AutoSnowFall(path, params, min_field);
          auto_snow.start();
        }
      }
      if (auto_snow != null)
      {
        throw new RuntimeException(String.format("Unable to select a field %d but snowfall is working on it", min_field));
      }
      else
      {
        throw new RuntimeException(String.format("Unable to select a field of at least %d.  Availible: %s", min_field, availible_fields.keySet().toString()));
      }
    }

    return i;
  }

  public SnowMerkleProof getFieldProof(int f)
  {
    return availible_fields.get(f);
  }

  public SnowMerkleProof getSingleUserFieldProof(int f)
    throws Exception
  {
    if (!availible_fields.containsKey(f))
    {
      return null;
    }

    if (config.getBoolean("memfield") || config.getDoubleWithDefault("memfield_precache_gb", 0) > 0)
    {
      return getFieldProof(f);
    }

    String name = params.getFieldSeeds().get(f);
    File field_folder = new File(path, name);

    return new SnowMerkleProof(field_folder, name);
  }
}
