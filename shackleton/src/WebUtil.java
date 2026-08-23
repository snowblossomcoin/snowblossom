package snowblossom.shackleton;

import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

public class WebUtil
{
  public static class NotFoundException extends Exception
  {
    public NotFoundException(String msg) { super(msg); }
  }

  public static class RedirectException extends Exception
  {
    public final String location;
    public RedirectException(String location)
    {
      super("redirect to " + location);
      this.location = location;
    }
  }

  public static Map<String, String> parseQuery(String raw)
  {
    LinkedHashMap<String, String> map = new LinkedHashMap<>();
    if ((raw == null) || (raw.length() == 0)) return map;
    for (String part : raw.split("&"))
    {
      if (part.length() == 0) continue;
      int eq = part.indexOf('=');
      String k;
      String v;
      if (eq < 0)
      {
        k = part;
        v = "";
      }
      else
      {
        k = part.substring(0, eq);
        v = part.substring(eq + 1);
      }
      try
      {
        k = URLDecoder.decode(k, "UTF-8");
        v = URLDecoder.decode(v, "UTF-8");
      }
      catch (Exception e)
      {
        continue;
      }
      if (!map.containsKey(k)) map.put(k, v);
    }
    return map;
  }

  public static int intParam(Map<String, String> q, String key, int def)
  {
    String v = q.get(key);
    if (v == null) return def;
    try
    {
      return Integer.parseInt(v.trim());
    }
    catch (Exception e)
    {
      return def;
    }
  }

  public static int clamp(int v, int min, int max)
  {
    return Math.max(min, Math.min(max, v));
  }

  public static boolean isHex(String s)
  {
    if (s == null) return false;
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      boolean ok = ((c >= '0') && (c <= '9')) || ((c >= 'a') && (c <= 'f')) || ((c >= 'A') && (c <= 'F'));
      if (!ok) return false;
    }
    return true;
  }

  public static boolean isDigits(String s)
  {
    if ((s == null) || (s.length() == 0)) return false;
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      if ((c < '0') || (c > '9')) return false;
    }
    return true;
  }
}
