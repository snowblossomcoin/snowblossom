package snowblossom.lib.trie;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Assert;
import org.rocksdb.*;
import snowblossom.trie.proto.TrieNode;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;

public class TrieDBRocks extends TrieDB
{
	private RocksDB db;
  private WriteOptions sharedWriteOptions;

  public TrieDBRocks(File path)
		throws Exception
  {
    RocksDB.loadLibrary();

    Options options = new Options();

    options.setIncreaseParallelism(16);
    options.setCreateIfMissing(true);
    options.setAllowMmapReads(true);
    options.setAllowMmapWrites(true);

    sharedWriteOptions = new WriteOptions();
    sharedWriteOptions.setDisableWAL(false);
    sharedWriteOptions.setSync(false);

    db = RocksDB.open(options, path.getAbsolutePath());
  }

  public void save(TrieNode node)
	{
		ByteString prefix = node.getHash();
		ByteString data = node.toByteString();
		try
    {
		  db.put(sharedWriteOptions, prefix.toByteArray(), data.toByteArray());
    }
    catch(RocksDBException e)
    {
      throw new RuntimeException(e);
    }
		
	}
  public TrieNode load(ByteString key)
	{
    try
    {
		  byte[] r = db.get(key.toByteArray());
	  	if (r == null) return null;
  		return TrieNode.parser().parseFrom(r);
    }
    catch(InvalidProtocolBufferException e)
    {
      throw new RuntimeException(e);
    }
    catch(RocksDBException e)
    {
      throw new RuntimeException(e);
    }

	}

  public void bulkSave(TreeMap<ByteString, TrieNode> updates)
  {
    try
    {
      WriteBatch wb = new WriteBatch();
      
      for(Map.Entry<ByteString, TrieNode> me : updates.entrySet())
      {
        ByteString key = me.getKey();
        TrieNode node = me.getValue();
        if (node == null)
        {
          wb.remove(key.toByteArray());
        }
        else
        {
          Assert.assertEquals(key, node.getHash());
          wb.put(key.toByteArray(), node.toByteString().toByteArray());
        }
      }

      db.write(sharedWriteOptions, wb);
    }
    catch(RocksDBException e)
    {
      throw new RuntimeException(e);
    }
  }

  public void flush()
  {
    try
    {
      db.flush(new FlushOptions().setWaitForFlush(true));
    }
    catch(RocksDBException e)
    {
      throw new RuntimeException(e);
    }
  }
}
