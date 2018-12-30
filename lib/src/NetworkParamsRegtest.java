package snowblossom.lib;


import com.google.common.collect.ImmutableList;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


public class NetworkParamsRegtest extends NetworkParams
{

  @Override
  public BigInteger getMaxTarget()
  {
    return BlockchainUtil.getTargetForDiff(11);
  }

  @Override
  public String getAddressPrefix() { return "snowreg"; }

  @Override
  protected Map<Integer, SnowFieldInfo> genSnowFields()
  {
    TreeMap<Integer, SnowFieldInfo> map = new TreeMap<>();

    long mb = 1024L*1024L;

    map.put(0, new SnowFieldInfo("cricket",       1L * mb, "da4eb1edd0969e39f86a139b7b43ba0e",11));
    map.put(1, new SnowFieldInfo("shrew",         2L * mb, "6a52fd075f6f581a9e6fb7c0a30ec8fe",12));
    map.put(2, new SnowFieldInfo("stoat",         4L * mb, "4f1a1233f12f08cdf15b73e49cc1636f",13));
    map.put(3, new SnowFieldInfo("ocelot",        8L * mb, "4497d77025a5edf059ad5ab8f069a015",14));
    map.put(4, new SnowFieldInfo("pudu",         16L * mb, "ee9753a4d6f4dcb64faca5113f66a75d",15));
    map.put(5, new SnowFieldInfo("badger",       32L * mb, "4c8395c86c195a997af873ec34ba5366",16));
    map.put(6, new SnowFieldInfo("capybara",     64L * mb, "64b276dd37f50790acf749eeeed3d56e",17));
    map.put(7, new SnowFieldInfo("llama",       128L * mb, "08bfebc48f74292e9714238533c7859b",18));
    map.put(8, new SnowFieldInfo("bumbear",     256L * mb, "c9ed0de2305a244e7e43d2757b1202c2",19));
    map.put(9, new SnowFieldInfo("hippo",       512L * mb, "46297d54bdd557c4098143e177edaaca",20));
    map.put(10,new SnowFieldInfo("shai-hulud", 1024L * mb, "656670bb7ff68a3c769e58a213c283e4",21));

    return map;
  }

  @Override
  public String getNetworkName() { return "spoon"; }

  @Override
  public int getBIP44CoinNumber() { return 2339; }

  @Override
  public long getBlockTimeTarget() { return 1000L; } //1 second

  @Override
  public List<String> getSeedNodes()
  {
    return ImmutableList.of("seed-regtest.snowblossom.org");
  }
  public int getDefaultPort() { return 2340; }


  @Override
  public int getActivationHeightTxOutRequirements() { return 1000; }

  @Override
  public int getActivationHeightTxOutExtras() { return 750; }
}
