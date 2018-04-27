package snowblossom.db.rocksdb;

import snowblossom.db.DBMap;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import java.util.concurrent.Executor;

import com.google.protobuf.ByteString;


public class RocksDBMap extends DBMap
{
  RocksDB db;
  String name;
  JRocksDB jdb;

  public RocksDBMap(JRocksDB jdb, RocksDB db, String name)
  {
    this.db = db;
    this.name = name;
    this.jdb = jdb;
  }

  public ByteString get(String key)
  {
    String key_str = name + "/" + key;

    try
    {

      byte[] r = db.get(key_str.getBytes());
      if (r == null) return null;

      return ByteString.copyFrom(r);

    }
    catch(RocksDBException e)
    {
      throw new RuntimeException(e);
    }

  }

  public void put(String key, ByteString value)
  {
    try
    {
      String key_str = name + "/" + key;

      db.put(jdb.getWriteOption(), key_str.getBytes(), value.toByteArray());

    }
    catch(RocksDBException e)
    {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void putAll(SortedMap<String, ByteString> m)
  {
    try
    {
      WriteBatch batch = new WriteBatch();

      /*SortedMap<String, ByteString> sorted_map = null;
      if (m instanceof SortedMap)
      {
        sorted_map = (SortedMap) m;
      }
      else
      {
        sorted_map = new TreeMap<>();
        sorted_map.putAll(m);
      }*/

      for(Map.Entry<String, ByteString> e : m.entrySet())
      {
        String key_str = name + "/" + e.getKey();
        batch.put(key_str.getBytes(), e.getValue().toByteArray());

      }

      db.write(jdb.getWriteOption(), batch);

    }
    catch(RocksDBException e)
    {
      throw new RuntimeException(e);
    }

  }


}
