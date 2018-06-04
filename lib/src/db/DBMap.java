package lib.src.db;

import com.google.protobuf.ByteString;
import duckutil.TimeRecord;

import java.util.Map;
import java.util.SortedMap;

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

  public boolean containsKey(ByteString key)
  {
    return get(key) != null;
  }
  public boolean containsKey(String key)
  {
    return containsKey(ByteString.copyFrom(key.getBytes()));
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

  
}

