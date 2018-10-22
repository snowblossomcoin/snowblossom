package snowblossom.lib.db;

import com.google.protobuf.ByteString;
import duckutil.TimeRecord;

import java.util.Map;
import java.util.SortedMap;
import java.util.List;

public abstract class DBMap
{

  public ByteString get(String key)
  {
    return get(ByteString.copyFrom(key.getBytes()));
  }

  public void put(String key, ByteString value)
  {
    put(ByteString.copyFrom(key.getBytes()), value);
  }

  public abstract ByteString get(ByteString key);
  public abstract void put(ByteString key, ByteString value);
  public void remove(ByteString key)
  {
    throw new RuntimeException("NOT IMPLEMENTED");
  }

  public boolean containsKey(ByteString key)
  {
    return get(key) != null;
  }
  public boolean containsKey(String key)
  {
    return containsKey(ByteString.copyFrom(key.getBytes()));
  }
  public Map<ByteString, ByteString> getByPrefix(ByteString prefix, int max_reply)
  {
    throw new RuntimeException("NOT IMPLEMENTED");
  }

  /** Implementing class should override this if they have something better to do */
  public void putAll(SortedMap<ByteString, ByteString> m)
  {
    long t1 = System.nanoTime();
    for(Map.Entry<ByteString, ByteString> me : m.entrySet())
    {
      put(me.getKey(), me.getValue());
    }
    TimeRecord.record(t1, "db_putall_seq");
  }

  /** 
   * Return a list of keys near target 'key'.  Doing 'count' before and 'count' after.
   * Includes wraping around the end of the map as needed to get the count.
   * For example, sending in count=3 will try to return 6 elements, 3 in each direction.
   */
  public List<ByteString> getClosestKeys(ByteString key, int count)
  {
    throw new RuntimeException("NOT IMPLEMENTED");
  }
  
}

