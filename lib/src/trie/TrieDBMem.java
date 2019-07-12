package snowblossom.lib.trie;

import com.google.protobuf.ByteString;
import java.util.HashMap;
import snowblossom.trie.proto.TrieNode;

public class TrieDBMem extends TrieDB
{
  private HashMap<ByteString, TrieNode> map;

  public TrieDBMem()
  {
    map = new HashMap<>(16,0.5f);
  }

  public void save(TrieNode node)
  {
    map.put(node.getHash(), node);
  }
  public TrieNode load(ByteString key)
  {
    return map.get(key);
  }
}
