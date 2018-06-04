package snowblossom.lib.db;

import com.google.protobuf.ByteString;
import duckutil.TimeRecord;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public abstract class DBMapMutationSet
{
  public abstract void add(ByteString key, ByteString value);

  /** Override this if the DB can do something better */
  public void addAll(Collection<Map.Entry<ByteString, ByteString> > lst)
  {
    long t1 = System.nanoTime();
    for(Map.Entry<ByteString, ByteString> me : lst)
    {
      add(me.getKey(), me.getValue());
    }
    TimeRecord.record(t1, "db_putmutset_seq");
  }

  public abstract Set<ByteString> getSet(ByteString key, int max_reply);

  public abstract void remove(ByteString key, ByteString value);

  /** Override this if the DB can do something better */
  public void removeAll(Collection<Map.Entry<ByteString, ByteString>> lst)
  {
    long t1 = System.nanoTime();
    for(Map.Entry<ByteString, ByteString> me : lst)
    {
      remove(me.getKey(), me.getValue());
    }
    TimeRecord.record(t1, "db_rmmutset_seq");
  }
}
