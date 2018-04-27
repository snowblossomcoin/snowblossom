package snowblossom;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.TreeMap;


public abstract class NetworkParams
{

  protected final ImmutableMap<Integer, SnowFieldInfo> snow_fields;

  public NetworkParams()
  {
    snow_fields = ImmutableMap.copyOf(genSnowFields());
  }

  protected abstract Map<Integer, SnowFieldInfo> genSnowFields();

  public SnowFieldInfo getSnowFieldInfo(int field) {return snow_fields.get(field); }

  public abstract String getNetworkName();

  // If this times 1000 is more than Long.MAX_VALUE, there will be trouble
  // in the running average calculation so probably always want to shift by at least 10.
  public long getMaxTarget()
  {
    return 1L << (64 - 12); //should probably be 24 to start
  }

  /** Get the weighting to use for running averages, in parts per 1000 */
  public long getAvgWeight() { return 10L; }

  public long getBlockTimeTarget() { return 600L * 1000L; } //10 minutes

  /**
   * Returns a mapping of field seeds, which also
   * should be the directory fields are stored in for miners
   * mapping to the field number.  This allows miners to see which fields
   * they have locally and support by looking for directories of those names.
   */
  public Map<Integer, String> getFieldSeeds()
  {
    Map<Integer, String> field_seed_map = new TreeMap<Integer, String>();

    for(int field : snow_fields.keySet())
    {
      String name = getNetworkName() + "." + field;
      field_seed_map.put(field, name);
    }
    return field_seed_map;
  }

}
