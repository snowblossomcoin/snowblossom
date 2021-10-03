package snowblossom.lib;

import com.google.common.collect.ImmutableList;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class NetworkParamsDemoShard extends NetworkParams
{

  @Override
  public BigInteger getMaxTarget()
  {
    return BlockchainUtil.getTargetForDiff(15);
  }

  @Override
  public String getAddressPrefix() { return "snowdemoshard"; }

  @Override
  protected Map<Integer, SnowFieldInfo> genSnowFields()
  {
    TreeMap<Integer, SnowFieldInfo> map = new TreeMap<>();

    long mb = 1024L*1024L;

    map.put(0, new SnowFieldInfo("cricket",       1L * mb, "ffcafdc2dae74d95be5b0574a1bef52e",18));
    map.put(1, new SnowFieldInfo("shrew",         2L * mb, "0456d4b3ee8d66736344cf2f07edda0c",19));
    map.put(2, new SnowFieldInfo("stoat",         4L * mb, "8ca5c4675f0f47755351cb41aa06696c",20));
    map.put(3, new SnowFieldInfo("ocelot",        8L * mb, "fe410a0fdbef0cf5ace856f383d2fdc3",21));
    map.put(4, new SnowFieldInfo("pudu",         16L * mb, "0dbc8bdc0466a528debf682637e6f01e",22));
    map.put(5, new SnowFieldInfo("badger",       32L * mb, "54799b27127a995f0d7e8fe8e12832e7",23));
    map.put(6, new SnowFieldInfo("capybara",     64L * mb, "9de79baf819f55d26a693d3dad8e5eb1",24));
    map.put(7, new SnowFieldInfo("llama",       128L * mb, "196a15c50d2a8352fc43aa47bbe410fc",25));
    map.put(8, new SnowFieldInfo("bugbear",     256L * mb, "0e8acc252f6f2e1218a5b9a9c3a465b8",26));
    map.put(9, new SnowFieldInfo("hippo",       512L * mb, "d3b6bcf094dee505fbb0212df7390df5",27));
    map.put(10,new SnowFieldInfo("shai-hulud", 1024L * mb, "42646cb78c40a7cd28f6fd37319edb8f",28));

    return map;
  }

  @Override
  public long getAvgWeight() { return 100L; }

  @Override
  public String getNetworkName() { return "demoshard"; }
  
  @Override
  public boolean allowSingleHost() { return true; }

  @Override
  public int getBIP44CoinNumber() { return 2340; }

  @Override
  public long getBlockTimeTarget() { return 600000L; } //10 min

  @Override
  public int getMaxBlockSize(){ return 4000000; } //4mb

  @Override
  public List<String> getSeedNodes()
  {
    return ImmutableList.of();
  }
  @Override
  public int getDefaultPort() { return 2371; }

  @Override
  public int getDefaultTlsPort() { return 2372; }

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
  //public int getMaxShardId() {return 14; } //allows 8 shards
  //public int getMaxShardId() {return 30; } //allows 16 shards
  //public int getMaxShardId() {return 62; } // allows 32 shards
  //public int getMaxShardId() {return 126; } //allows 64 shards
  //public int getMaxShardId() {return 254; } // allows 128 shards
  //public int getMaxShardId() {return 512; } //allows 256 shards
  //public int getMaxShardId() {return 1022; } //allows 512 shards
  
  @Override
  public int getMaxShardSkewHeight() {return 6; } 
    
  @Override
  public int getShardForkThreshold() { return 2000; }


}
