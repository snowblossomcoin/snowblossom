package snowblossom.lib.trie;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import snowblossom.lib.db.DBMap;
import snowblossom.trie.proto.TrieNode;

import java.util.Map;
import java.util.TreeMap;


public class TrieDBMap extends TrieDB
{
  private DBMap db_map;
  public TrieDBMap(DBMap db_map)
  {
    this.db_map = db_map;
  }

  @Override
  public void save(TrieNode node)
  {
    db_map.put( node.getHash(), node.toByteString());
  }

  @Override
  public TrieNode load(ByteString key)
  {
    ByteString r = db_map.get(key);
    if (r == null) return null;
		try
		{
		 	return TrieNode.parser().parseFrom(r);
    }
    catch(InvalidProtocolBufferException e)
    {
      throw new RuntimeException(e);
    }

  }

  @Override
  public void bulkSave(TreeMap<ByteString, TrieNode> updates)
  {
    TreeMap<ByteString, ByteString> map = new TreeMap<>(new ByteStringComparator());

    for(Map.Entry<ByteString, TrieNode> me : updates.entrySet())
    {
      ByteString key = me.getKey();
      ByteString value = me.getValue().toByteString();
      map.put(key, value);
    }

    db_map.putAll(map);

  }
}
