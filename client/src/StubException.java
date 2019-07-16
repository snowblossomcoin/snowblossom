package snowblossom.client;

import java.util.Map;
import java.util.TreeMap;

public class StubException extends Exception
{

  protected Map<String, String> error_map;

  public StubException(String msg)
  {
    this(msg, new TreeMap<String,String>());
  }
  public StubException(String msg, Map<String, String> error_map)
  {
    super(msg);

    this.error_map = error_map;
  }

  public Map<String,String> getErrorMap()
  {
    return error_map;
  }
}
