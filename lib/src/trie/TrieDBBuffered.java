package snowblossom.lib.trie;

import com.google.protobuf.ByteString;
import snowblossom.trie.proto.TrieNode;

import java.util.TreeMap;


public class TrieDBBuffered extends TrieDB
{
  private TreeMap<ByteString, TrieNode> changes;

  TrieDB db;

  public TrieDBBuffered(TrieDB inner)
  {
    this.db = inner;
    changes = new TreeMap<>(new ByteStringComparator());
  }

  public void save(TrieNode node)
  {
    changes.put(node.getHash(), node);
  }
  public TrieNode load(ByteString key)
  {
    if (changes.containsKey(key)) return changes.get(key);

    return db.load(key);
  }

  public void commit()
  {
    db.bulkSave(changes);
    changes.clear();
  }
}
