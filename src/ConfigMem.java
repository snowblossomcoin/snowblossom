package snowblossom;
import java.util.Map;


public class ConfigMem extends Config
{
  private Map<String, String> map;

  public ConfigMem(Map<String, String> map)
  {
    this.map = map;
  }

  @Override
  public String get(String key)
  {
    return map.get(key);
  }
}
