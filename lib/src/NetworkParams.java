package snowblossom.lib;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import duckutil.Config;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class NetworkParams
{
	private static final Logger logger = Logger.getLogger("snowblossom.blockchain");

  protected final ImmutableMap<Integer, SnowFieldInfo> snow_fields;

  public NetworkParams()
  {
    snow_fields = ImmutableMap.copyOf(genSnowFields());
  }

  protected abstract Map<Integer, SnowFieldInfo> genSnowFields();

  public SnowFieldInfo getSnowFieldInfo(int field) {return snow_fields.get(field); }

  public abstract String getNetworkName();
  public abstract int getBIP44CoinNumber();

  public abstract String getAddressPrefix();

  public BigInteger getMaxTarget()
  {
    return BlockchainUtil.getTargetForDiff(24);
  }

  /** Get the weighting to use for running averages, in parts per 1000 */
  public long getAvgWeight() { return 10L; }

  public long getBlockTimeTarget() { return 600L * 1000L; } //10 minutes

  public ByteString getBlockZeroRemark() { return ByteString.copyFrom(new String("it begins").getBytes()); }

  /**
   * Use NTP
   */
  public long getMaxClockSkewMs() { return 45000; }

  public abstract List<String> getSeedNodes();
  public int getDefaultPort() { return 2338; }

  public int getActivationHeightTxOutRequirements() { return Integer.MAX_VALUE; }
  public int getActivationHeightTxOutExtras() { return Integer.MAX_VALUE; }

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

  public static NetworkParams loadFromConfig(Config config)
  {
    if (config.isSet("network"))
    {
      String network = config.get("network");

      if (network.equals("snowblossom"))
      {
        return new NetworkParamsProd();
      }
      else if (network.equals("mainnet"))
      {
        return new NetworkParamsProd();
      }
      else if (network.equals("testnet"))
      {
        logger.info("Using network teapot - testnet");
        return new NetworkParamsTestnet();
      }
      else if (network.equals("teapot"))
      {
        logger.info("Using network teapot - testnet");
        return new NetworkParamsTestnet();
      }
      else if (network.equals("spoon"))
      {
        logger.info("Using network spoon - regtest");
        return new NetworkParamsRegtest();
      }
      else if (network.equals("regtest"))
      {
        logger.info("Using network spoon - regtest");
        return new NetworkParamsRegtest();
      }
      else
      {
        logger.log(Level.SEVERE, String.format("Unknown network: %s", network));
				return null;
      }
    }
    else
    {
      return new NetworkParamsProd();
    }

  }
}
