package snowblossom.miner;

import snowblossom.NetworkParams;
import snowblossom.SnowMerkleProof;
import snowblossom.SnowFieldInfo;
import snowblossom.proto.SnowPowProof;

import java.io.File;

import java.util.logging.Logger;
import java.util.Random;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import com.google.common.collect.ImmutableSortedMap;

public class FieldScan
{

  private static final Logger logger = Logger.getLogger("FieldScan");

  private File path;
  private NetworkParams params;

  private ImmutableSortedMap<Integer, SnowMerkleProof> availible_fields;

  public FieldScan(File path, NetworkParams params)
  {
    this.path = path;
    this.params = params;
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
          SnowMerkleProof proof = new SnowMerkleProof(field_folder, name);

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
    Random rnd = new Random();

    // If the field ends up with more than 32 bits of words, just check one from
    // the first part. meh.
    long max = Math.min((long)Integer.MAX_VALUE, proof.getTotalWords());

    long check = rnd.nextInt((int)max);
    SnowPowProof p = proof.getProof(check);

    SnowFieldInfo info = params.getSnowFieldInfo(field_number);

    if (!SnowMerkleProof.checkProof(p, info.getMerkleRootHash(), info.getLength() ))
    {
      throw new RuntimeException("proof check failed");
    }
  }

  public int selectField(int min_field)
  {
    Integer i = availible_fields.ceilingKey(min_field);
    if (i == null) throw new RuntimeException(String.format("Unable to select a field of at least %d.  Availible: %s", min_field, availible_fields.keySet().toString()));

    return i;
  }

  public SnowMerkleProof getFieldProof(int f)
  {
    return availible_fields.get(f);
  }

}
