package snowblossom;


import java.util.Map;
import java.util.TreeMap;

import java.util.List;
import com.google.common.collect.ImmutableList;


public class NetworkParamsProd extends NetworkParams
{
  @Override
  public long getMaxTarget()
  {
    return 1L << (64 - 25); //should probably be 24 to start
  }

  @Override
  public String getAddressPrefix() { return "snow"; }

  @Override
  protected Map<Integer, SnowFieldInfo> genSnowFields()
  {
    TreeMap<Integer, SnowFieldInfo> map = new TreeMap<>();

    long gb = 1024L*1024L*1024L;

    map.put(0, new SnowFieldInfo("cricket",       1L * gb, "2c363d33550f5c4da16279d1fa89f7a9",25));
    map.put(1, new SnowFieldInfo("shrew",         2L * gb, "4626ba6c78e0d777d35ae2044f8882fa",27));
    map.put(2, new SnowFieldInfo("stoat",         4L * gb, "2948b321266dfdec11fbdc45f46cf959",29));
    map.put(3, new SnowFieldInfo("ocelot",        8L * gb, "33a2fb16f08347c2dc4cbcecb4f97aeb",31));
    map.put(4, new SnowFieldInfo("pudu",         16L * gb, "0cdcb8629bef77fbe1f9b740ec3897c9",33));
    map.put(5, new SnowFieldInfo("badger",       32L * gb, "e9abff32aa7f74795be2aa539a079489",35));
    map.put(6, new SnowFieldInfo("capybara",     64L * gb, "e68678fadb1750feedfa11522270497f",37));
    map.put(7, new SnowFieldInfo("llama",       128L * gb, "147d379701f621ebe53f5a511bd6c380",39));
    map.put(8, new SnowFieldInfo("bugbear",     256L * gb, "533ae42a04a609c4b45464b1aa9e6924",41));
    map.put(9, new SnowFieldInfo("hippo",       512L * gb, "ecdb28f1912e2266a71e71de601117d2",43));
    map.put(10,new SnowFieldInfo("shai-hulud", 1024L * gb, "cc883468a08f48b592a342a2cdf5bcba",45));
    map.put(11,new SnowFieldInfo("avanc", 		 2048L * gb, "a2a4076f6cde947935db06e5fc5bbd14",47));

    return map;
  }

  @Override
  public String getNetworkName() { return "snowblossom"; }

  @Override
  public List<String> getSeedNodes()
  {
    return ImmutableList.of("seed.snowblossom.org");
  }
  @Override
  public int getDefaultPort() { return 2338; }
}
