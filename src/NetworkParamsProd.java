package snowblossom;


import java.util.Map;
import java.util.TreeMap;


public class NetworkParamsProd extends NetworkParams
{

  @Override
  protected Map<Integer, SnowFieldInfo> genSnowFields()
  {
    TreeMap<Integer, SnowFieldInfo> map = new TreeMap<>();

    long gb = 1024L*1024L*1024L;

    map.put(0, new SnowFieldInfo("cricket",       1L * gb, "2c363d33550f5c4da16279d1fa89f7a9"));
    map.put(1, new SnowFieldInfo("shrew",         2L * gb, "4626ba6c78e0d777d35ae2044f8882fa"));
    map.put(2, new SnowFieldInfo("stoat",         4L * gb, "2948b321266dfdec11fbdc45f46cf959"));
    map.put(3, new SnowFieldInfo("ocelot",        8L * gb, "33a2fb16f08347c2dc4cbcecb4f97aeb"));
    map.put(4, new SnowFieldInfo("pudu",         16L * gb, "0cdcb8629bef77fbe1f9b740ec3897c9"));
    map.put(5, new SnowFieldInfo("badger",       32L * gb, "e9abff32aa7f74795be2aa539a079489"));
    map.put(6, new SnowFieldInfo("capybara",     64L * gb, "e68678fadb1750feedfa11522270497f"));
    map.put(7, new SnowFieldInfo("llama",       128L * gb, "147d379701f621ebe53f5a511bd6c380"));
    map.put(8, new SnowFieldInfo("bugbear",     256L * gb, "533ae42a04a609c4b45464b1aa9e6924"));
    map.put(9, new SnowFieldInfo("hippo",       512L * gb, "ecdb28f1912e2266a71e71de601117d2"));
    map.put(10,new SnowFieldInfo("shai-hulud", 1024L * gb, "cc883468a08f48b592a342a2cdf5bcba"));

    return map;
  }

  @Override
  public String getNetworkName() { return "snowblossom"; }

}
