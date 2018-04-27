package snowblossom.db;

import snowblossom.TimeRecord;
import com.google.protobuf.ByteString;
import java.util.SortedMap;
import java.util.Map;

public abstract class DBMap
{

  public abstract ByteString get(String key);
  public abstract void put(String key, ByteString value);

  public boolean containsKey(String key)
  {
    return get(key) != null;
  }

  /** Implementing class should override this if they have something better to do */
  public void putAll(SortedMap<String, ByteString> m)
  {
    long t1 = System.nanoTime();
    for(Map.Entry<String, ByteString> me : m.entrySet())
    {
      put(me.getKey(), me.getValue());
    }
    TimeRecord.record(t1, "db_putall_seq");
  }

  
}

