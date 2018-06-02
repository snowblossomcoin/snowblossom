package snowblossomlib.db.rocksdb;

import com.google.protobuf.ByteString;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import snowblossomlib.db.DBMap;

import java.util.Map;
import java.util.SortedMap;


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
  public void putAll(SortedMap<ByteString, ByteString> m)
  {
    try
    {
      WriteBatch batch = new WriteBatch();

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


}
