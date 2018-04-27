package snowblossom;


import java.util.Map;
import java.util.TreeMap;


public class NetworkParamsTestnet extends NetworkParams
{

  @Override
  protected Map<Integer, SnowFieldInfo> genSnowFields()
  {
    TreeMap<Integer, SnowFieldInfo> map = new TreeMap<>();

    long mb = 1024L*1024L;

    map.put(0, new SnowFieldInfo("cricket",       1L * mb, "bacc447f89ee2b623721083ed9437842"));
    map.put(1, new SnowFieldInfo("shrew",         2L * mb, "27aa4228bf39c6b6a5164ea3e050bfcc"));
    map.put(2, new SnowFieldInfo("stoat",         4L * mb, "47873faca24808fbb334af74ffa3ce08"));
    map.put(3, new SnowFieldInfo("ocelot",        8L * mb, "97f8303394d267dfe4bf65243bd3740e"));
    map.put(4, new SnowFieldInfo("pudu",         16L * mb, "3587f96c4e6651dd900e462d3404c03d"));
    map.put(5, new SnowFieldInfo("badger",       32L * mb, "ca1cedce13472a2e030c5c91933879a7"));
    map.put(6, new SnowFieldInfo("capybara",     64L * mb, "0b3297f9b20e38ecfd66bc881a19412f"));
    map.put(7, new SnowFieldInfo("llama",       128L * mb, "72ec45325df2b918aaba7891491e0ad0"));
    map.put(8, new SnowFieldInfo("bumbear",     256L * mb, "6b1b0b675d6ecd3f6a07a924eb920754"));
    map.put(9, new SnowFieldInfo("hippo",       512L * mb, "25cee1c0e6b5e6c7bbeee0756e0ca6cd"));
    map.put(10,new SnowFieldInfo("shai-hulud", 1024L * mb, "b30e10f6c72b72f244cfddf5932f65f4"));

    return map;
  }

  @Override
  public String getNetworkName() { return "teapot"; }

}
