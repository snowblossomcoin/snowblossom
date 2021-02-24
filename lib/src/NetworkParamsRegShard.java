package snowblossom.lib;

import com.google.common.collect.ImmutableList;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class NetworkParamsRegShard extends NetworkParams
{

  @Override
  public BigInteger getMaxTarget()
  {
    return BlockchainUtil.getTargetForDiff(11);
  }

  @Override
  public String getAddressPrefix() { return "snowshardo"; }

  @Override
  protected Map<Integer, SnowFieldInfo> genSnowFields()
  {
    TreeMap<Integer, SnowFieldInfo> map = new TreeMap<>();

    long mb = 1024L*1024L;

    map.put(0, new SnowFieldInfo("cricket",       1L * mb, "cfb81749b78efc20e8cc4343f824c9fa",11));
    map.put(1, new SnowFieldInfo("shrew",         2L * mb, "7f15a7aa5efc42353f431e6cbe8a2c88",12));
    map.put(2, new SnowFieldInfo("stoat",         4L * mb, "e0158ab6fb8c1550a3a4da0dead21cdc",13));
    map.put(3, new SnowFieldInfo("ocelot",        8L * mb, "2016e50974facef60276c9b5c849f2f8",14));
    map.put(4, new SnowFieldInfo("pudu",         16L * mb, "57daabe01e9dbf7807eba189da61f6c7",15));
    map.put(5, new SnowFieldInfo("badger",       32L * mb, "c671013f8bbf9c689ea66d0381fdfdf4",16));
    map.put(6, new SnowFieldInfo("capybara",     64L * mb, "e02aadbc39e2554c569d02fc724b4e95",17));
    map.put(7, new SnowFieldInfo("llama",       128L * mb, "5110f56663773a6633ea1144aa32e4ad",18));
    map.put(8, new SnowFieldInfo("bumbear",     256L * mb, "0d1dd0394069830ade8049f6f0973377",19));
    map.put(9, new SnowFieldInfo("hippo",       512L * mb, "74952fbc546e4026a5d1e295dd95b3eb",20));
    map.put(10,new SnowFieldInfo("shai-hulud", 1024L * mb, "cbd186ceab88649ff178c9e15910b265",21));

    return map;
  }

  @Override
  public long getAvgWeight() { return 100L; }

  @Override
  public String getNetworkName() { return "regshard"; }
  
  @Override
  public boolean allowSingleHost() {return true; }

  @Override
  public int getBIP44CoinNumber() { return 2340; }

  @Override
  public long getBlockTimeTarget() { return 1000L; } //1 second

  @Override
  public int getMaxBlockSize(){ return 3800000; }

  @Override
  public List<String> getSeedNodes()
  {
    return ImmutableList.of("seed-reg-shard.snowblossom.org");
  }
  @Override
  public int getDefaultPort() { return 2341; }

  @Override
  public int getDefaultTlsPort() { return 2351; }

  @Override
  public int getActivationHeightTxOutRequirements() { return 0; }

  @Override
  public int getActivationHeightTxOutExtras() { return 0; }

  @Override
  public int getActivationHeightTxInValue() { return 0; }

  @Override
  public int getActivationHeightShards() { return 10; }

  @Override
  public int getMinShardLength() { return 10; }

  @Override
  public int getShardForkThreshold() {return 0; }



}
