package snowblossom.lib.db.rocksdb;

import com.google.protobuf.ByteString;
import snowblossom.lib.db.DBMap;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.RocksIterator;

import java.util.Map;
import java.util.SortedMap;
import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import snowblossom.lib.db.DBTooManyResultsException;

public class RocksDBMap extends DBMap
{
  RocksDB db;
  ByteString prefix;
  JRocksDB jdb;

  public RocksDBMap(JRocksDB jdb, RocksDB db, String name)
  {
    this.db = db;
    this.jdb = jdb;
    String prefix_str = name + "/";

    this.prefix = ByteString.copyFrom(prefix_str.getBytes());
  }

  public ByteString get(ByteString key)
  {
    ByteString key_str = prefix.concat(key);

    try
    {

      byte[] r = db.get(key_str.toByteArray());
      if (r == null) return null;

      return ByteString.copyFrom(r);

    }
    catch(RocksDBException e)
    {
      throw new RuntimeException(e);
    }
  }

  public void put(ByteString key, ByteString value)
  {
    try
    {
      ByteString key_str = prefix.concat(key);
      db.put(jdb.getWriteOption(), key_str.toByteArray(), value.toByteArray());
    }
    catch(RocksDBException e)
    {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void remove(ByteString key)
  {
    try
    {
      ByteString key_str = prefix.concat(key);
      db.delete(jdb.getWriteOption(), key_str.toByteArray());
    }
    catch(RocksDBException e)
    {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void putAll(SortedMap<ByteString, ByteString> m)
  {
    try(WriteBatch batch = new WriteBatch())
    {

      for(Map.Entry<ByteString, ByteString> e : m.entrySet())
      {
        ByteString key_str = prefix.concat(e.getKey());
        batch.put(key_str.toByteArray(), e.getValue().toByteArray());

      }

      db.write(jdb.getWriteOption(), batch);

    }
    catch(RocksDBException e)
    {
      throw new RuntimeException(e);
    }

  }

  @Override
  public List<ByteString> getClosestKeys(ByteString key, int count)
  {
    ByteString key_str = prefix.concat(key);
    LinkedList<ByteString> lst = new LinkedList<>();

    try(RocksIterator it = db.newIterator())
    {
      it.seek(key_str.toByteArray());
      
      // Empty set, doomed
      if (!it.isValid()) return lst;

      for(int i=0; i<count; i++)
      {
        lst.add(ByteString.copyFrom(it.key()).substring(prefix.size()));
        it.next();
        if (it.isValid()) it.seekToFirst(); //wrap around
      }
      
      it.seek(key_str.toByteArray());
      for(int i=0; i<count; i++)
      {
        // First element covered by section above, so move then add
        it.prev();
        if (!it.isValid()) it.seekToLast();
        lst.add(ByteString.copyFrom(it.key()).substring(prefix.size()));
      }

      // This is really fun in the degenerate case where the map is shorter than count and we keep wraping.
      // as long as we have at least one element, this should be fine.

    }
    return lst;

  }

  @Override
  public Map<ByteString, ByteString> getByPrefix(ByteString key, int max_reply)
  {
    ByteString key_str = prefix.concat(key);

    LinkedList<ByteString> set = new LinkedList<>();
		Map<ByteString, ByteString> map = new HashMap<>(16,0.5f);

    int count = 0;
    RocksIterator it = db.newIterator();

    try
    {
      it.seek(key_str.toByteArray());

      while(it.isValid())
      {
        ByteString curr_key = ByteString.copyFrom(it.key());
        if (!curr_key.startsWith(key_str)) break;

        ByteString k = curr_key.substring(prefix.size());
        
       	map.put(k, ByteString.copyFrom(it.value()));
				count++;

        if (count > max_reply) throw new DBTooManyResultsException();

        it.next();
      }
    }
    finally
    {
      it.dispose();
    }

    return map;

  }

}
