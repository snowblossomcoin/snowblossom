package snowblossom.lib;


import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


public class NetworkParamsTestnet extends NetworkParams
{

  @Override
  public BigInteger getMaxTarget()
  {
    return BlockchainUtil.getTargetForDiff(22);
  }

  @Override
  public String getAddressPrefix() { return "snowtest"; }

  @Override
  protected Map<Integer, SnowFieldInfo> genSnowFields()
  {
    TreeMap<Integer, SnowFieldInfo> map = new TreeMap<>();

    long mb = 1024L*1024L;

    map.put(0, new SnowFieldInfo("cricket",       1L * mb, "bacc447f89ee2b623721083ed9437842",22));
    map.put(1, new SnowFieldInfo("shrew",         2L * mb, "27aa4228bf39c6b6a5164ea3e050bfcc",23));
    map.put(2, new SnowFieldInfo("stoat",         4L * mb, "47873faca24808fbb334af74ffa3ce08",24));
    map.put(3, new SnowFieldInfo("ocelot",        8L * mb, "97f8303394d267dfe4bf65243bd3740e",25));
    map.put(4, new SnowFieldInfo("pudu",         16L * mb, "3587f96c4e6651dd900e462d3404c03d",26));
    map.put(5, new SnowFieldInfo("badger",       32L * mb, "ca1cedce13472a2e030c5c91933879a7",27));
    map.put(6, new SnowFieldInfo("capybara",     64L * mb, "0b3297f9b20e38ecfd66bc881a19412f",28));
    map.put(7, new SnowFieldInfo("llama",       128L * mb, "72ec45325df2b918aaba7891491e0ad0",29));
    map.put(8, new SnowFieldInfo("bumbear",     256L * mb, "6b1b0b675d6ecd3f6a07a924eb920754",30));
    map.put(9, new SnowFieldInfo("hippo",       512L * mb, "25cee1c0e6b5e6c7bbeee0756e0ca6cd",31));
    map.put(10,new SnowFieldInfo("shai-hulud", 1024L * mb, "b30e10f6c72b72f244cfddf5932f65f4",32));

    return map;
  }

  @Override
  public String getNetworkName() { return "teapot"; }

  @Override
  public int getBIP44CoinNumber() { return 2339; }

  @Override
  public List<String> getSeedNodes()
  {
    return ImmutableList.of("seed-testnet.snowblossom.org");
  }
  @Override
  public int getDefaultPort() { return 2339; }

  @Override
  public ByteString getBlockZeroRemark() { return ByteString.copyFrom(new String("testnet2-20180516").getBytes()); }

  @Override
  public int getActivationHeightTxOutRequirements() { return 16000; }

  @Override
  public int getActivationHeightTxOutExtras() { return 16000; }

}
