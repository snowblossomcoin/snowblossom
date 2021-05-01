package snowblossom.lib;

import com.google.common.collect.ImmutableList;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class NetworkParamsTestShard extends NetworkParams
{

  @Override
  public BigInteger getMaxTarget()
  {
    return BlockchainUtil.getTargetForDiff(22);
  }

  @Override
  public String getAddressPrefix() { return "snowtestshard"; }

  @Override
  protected Map<Integer, SnowFieldInfo> genSnowFields()
  {
    TreeMap<Integer, SnowFieldInfo> map = new TreeMap<>();

    long mb = 1024L*1024L;

    map.put(0, new SnowFieldInfo("cricket",       1L * mb, "354baa73a383b8490cb8823a38c18ebf",22));
    map.put(1, new SnowFieldInfo("shrew",         2L * mb, "1fa306514996ec2402ff88c2014753aa",23));
    map.put(2, new SnowFieldInfo("stoat",         4L * mb, "81c021376ca7bbc9e95e29a9bc4540f7",24));
    map.put(3, new SnowFieldInfo("ocelot",        8L * mb, "9574e0e52ab38fcce62c758651842251",25));
    map.put(4, new SnowFieldInfo("pudu",         16L * mb, "83e5de1a60d119cd0a06bbe130800abd",26));
    map.put(5, new SnowFieldInfo("badger",       32L * mb, "b4df45d1b161dae5facd7b45a773d688",27));
    map.put(6, new SnowFieldInfo("capybara",     64L * mb, "c5e3ef1bc746a78882a86bc5e2952565",28));
    map.put(7, new SnowFieldInfo("llama",       128L * mb, "5df17dfc7907ee0816077795ddd6701d",29));
    map.put(8, new SnowFieldInfo("bugbear",     256L * mb, "28c012cac8bf777d6187bbfbd23d9a30",30));
    map.put(9, new SnowFieldInfo("hippo",       512L * mb, "4dc24fe284fa71d7f95060b947ebb0c0",31));
    map.put(10,new SnowFieldInfo("shai-hulud", 1024L * mb, "d19fcf03622b745bce14c3666beca537",32));

    return map;
  }

  @Override
  public long getAvgWeight() { return 100L; }

  @Override
  public String getNetworkName() { return "testshard"; }
  
  @Override
  public boolean allowSingleHost() { return true; }

  @Override
  public int getBIP44CoinNumber() { return 2340; }

  @Override
  public long getBlockTimeTarget() { return 600000L; } //10 min

  @Override
  public int getMaxBlockSize(){ return 64000000; } //64mb

  @Override
  public List<String> getSeedNodes()
  {
    return ImmutableList.of("seed-test-shard.snowblossom.org", "kube.1209k.com");
  }
  @Override
  public int getDefaultPort() { return 2361; }

  @Override
  public int getDefaultTlsPort() { return 2362; }

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
  public int getMaxShardId() {return 6; } //allows 4 shards
  
  @Override
  public int getMaxShardSkewHeight() {return 6; } 
    
  @Override
  public int getShardForkThreshold() { return getMaxBlockSize() / 256; }


}
