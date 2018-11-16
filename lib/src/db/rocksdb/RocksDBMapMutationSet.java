package snowblossom.lib.db.rocksdb;

import com.google.protobuf.ByteString;
import com.google.common.collect.TreeMultimap;
import snowblossom.lib.db.DBMapMutationSet;
import snowblossom.lib.db.DBTooManyResultsException;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.RocksIterator;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.LinkedList;
import java.util.List;

public class RocksDBMapMutationSet extends DBMapMutationSet
{
  JRocksDB jdb;
  RocksDB db;
  String name;
  byte[] name_bytes;
  byte sep = '/';
  public RocksDBMapMutationSet(JRocksDB jdb, RocksDB db, String name)
  {
    this.db = db;
    this.name = name;
    this.jdb = jdb;
    name_bytes = name.getBytes();
  }

  private ByteString getDBKey(ByteString key, ByteString value)
  {
		try
		{
      ByteString.Output out = ByteString.newOutput(100);
      out.write(name_bytes);
      out.write(key.toByteArray());
      out.write(sep);	
      out.write(value.toByteArray());
      ByteString w = out.toByteString();
      return w;
    }
    catch(java.io.IOException e)
    {
      throw new RuntimeException(e);
    }
  }
  private ByteString getDBKey(ByteString key)
  {
    try
    {
      ByteString.Output out = ByteString.newOutput(100);
      out.write(name_bytes);
      out.write(key.toByteArray());
      out.write(sep);
      ByteString w = out.toByteString();
      return w;
    }
    catch(java.io.IOException e)
    {
      throw new RuntimeException(e);
    }
  }


  @Override
  public void add(ByteString key, ByteString value)
  {
    byte b[]=new byte[0];

    ByteString w = getDBKey(key, value);
  
    try
    {
    	db.put(jdb.getWriteOption(), w.toByteArray(), b);
		}
    catch(RocksDBException e)
    {
      throw new RuntimeException(e);
    }

  }

  @Override
  public void addAll(TreeMultimap<ByteString, ByteString> map)
  {
    try(WriteBatch batch = new WriteBatch())
    {
      byte b[]=new byte[0];

      for(Map.Entry<ByteString, ByteString> me : map.entries())
      {
        ByteString w = getDBKey(me.getKey(), me.getValue());
        batch.put(w.toByteArray(), b);
      }

      db.write(jdb.getWriteOption(), batch);
    }
    catch(RocksDBException e)
    {
      throw new RuntimeException(e);
    }

  }

  @Override
  public void remove(ByteString key, ByteString value)
  {
		try
		{
    	ByteString w = getDBKey(key, value);
    	db.remove(jdb.getWriteOption(), w.toByteArray());
		}
    catch(RocksDBException e)
    {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<ByteString> getSet(ByteString key, int max_reply)
  {
		ByteString dbKey = getDBKey(key);

    LinkedList<ByteString> set = new LinkedList<>();
    int count = 0;
    try(RocksIterator it = db.newIterator())
    {
      it.seek(dbKey.toByteArray());

      while(it.isValid())
      {
				ByteString curr_key = ByteString.copyFrom(it.key());
        if (!curr_key.startsWith(dbKey)) break;

        ByteString v = curr_key.substring(dbKey.size());
        set.add(v);
        count++;

        if (count > max_reply) throw new DBTooManyResultsException();

        it.next();
      }
    }

    return set;

  }


}
