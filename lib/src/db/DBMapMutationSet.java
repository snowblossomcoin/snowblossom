package snowblossom.lib.db;

import com.google.protobuf.ByteString;
import duckutil.TimeRecord;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.List;
import com.google.common.collect.TreeMultimap;
import snowblossom.lib.trie.ByteStringComparator;

/**
 * Why the hell is this named this?
 * All this is is a multimap that looks something like:
 * Map<ByteString, Set<ByteString>> 
 */
public abstract class DBMapMutationSet
{
  public abstract void add(ByteString key, ByteString value);

  public static TreeMultimap<ByteString, ByteString> createMap()
  {
    return TreeMultimap.create(new ByteStringComparator(), new ByteStringComparator());
  }

  /** Override this if the DB can do something better */
  public void addAll(TreeMultimap<ByteString, ByteString> map)
  {
    long t1 = System.nanoTime();

    for(Map.Entry<ByteString, ByteString> me : map.entries())
    {
      add(me.getKey(), me.getValue());
    }
    TimeRecord.record(t1, "db_putmutset_seq");
  }

  public abstract List<ByteString> getSet(ByteString key, int max_reply);

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
