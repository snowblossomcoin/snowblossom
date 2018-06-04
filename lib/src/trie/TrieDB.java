package snowblossom.lib.trie;

import com.google.protobuf.ByteString;
import org.junit.Assert;
import snowblossom.trie.proto.TrieNode;

import java.util.Map;
import java.util.TreeMap;

public abstract class TrieDB
{
  public abstract void save(TrieNode node);
  public abstract TrieNode load(ByteString key);

  public void bulkSave(TreeMap<ByteString, TrieNode> updates)
  {
    for(Map.Entry<ByteString, TrieNode> me : updates.entrySet())
    {
      ByteString key = me.getKey();
      TrieNode node = me.getValue();
      Assert.assertEquals(key, node.getHash());
      save(node);
    }

  }
}
