package snowblossom.lib.trie;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.protobuf.ByteString;
import java.util.*;
import org.junit.Assert;
import snowblossom.trie.proto.ChildEntry;
import snowblossom.trie.proto.TrieNode;

/**
 * So this is a trie with the following properties:
 * - each update results in a new root node
 * - the hash of a previously created root node is used to access to the trie for that version
 *   (this of each root hash as a snapshot id)
 * - mutations from one root hash produces a new root hash
 * - mutations may be performed from any previous root hash
 * - mutations are idempotent
 * - the same set of keys and values should also hash to the same value regardless of path to reach that mapping
 */
public class HashedTrie
{
  private final TrieDB basedb;
  private final boolean end_cap_data;

  public static final ByteString DATA_START = ByteString.copyFrom("DATA<".getBytes());
  public static final ByteString DATA_END = ByteString.copyFrom(">DATA".getBytes());

  /**
   * @param db The DB to store things in
   * @param create_if_empty create empty root if not present
   * @param end_cap_data Wrap any data with the DATA_START and DATA_END above.  Important to be true
       for variable key length Trie's, since otherwise someone could play silly buggers by removing
       the children of a node and putting in data equals the hashes of the children and it would hash the same.
   */
  public HashedTrie(TrieDB db, boolean create_if_empty, boolean end_cap_data)
  {
    this.basedb = db;
    this.end_cap_data = end_cap_data;

    TrieNode root = db.load(HashUtils.hashOfEmpty());
    if ((root == null) && (create_if_empty))
    {
      TrieNode.Builder builder = TrieNode.newBuilder();
      builder.setPrefix(ByteString.EMPTY);
      builder.setHash(HashUtils.hashOfEmpty());
      root = builder.build();
      db.save(root);
      Assert.assertEquals(root.getHash(), HashUtils.hashOfEmpty());
    }
    Assert.assertNotNull(root);
  }

  /** 
   * Merge in the following updates.
   * A null value means remove that entry if it exists
   * @return the new root hash
   */
  public ByteString mergeBatch(ByteString root_hash, Map<ByteString, ByteString> updates)
  {
    TrieDBBuffered db = new TrieDBBuffered(basedb);

    TrieNode root = db.load(root_hash);
    ByteString answer = mergeNode(db, root, updates).getHash();
    db.commit();
    return answer;
  }

  public boolean mergeIfNewRoot(ByteString old_root, Map<ByteString, ByteString> updates, ByteString expected_new_root)
  {
    TrieDBBuffered db = new TrieDBBuffered(basedb);
    TrieNode root = db.load(old_root);
    
    ByteString answer = mergeNode(db, root, updates).getHash();
    if (answer.equals(expected_new_root))
    {
      System.out.println("Commiting new UTXO root: " + HashUtils.getHexString(answer));
      db.commit();
      return true;
    }
    return false;
  }

  /**
   * See what the new UTXO hash would be with these changes
   * without commiting them
   */
  public ByteString simulateMerge(ByteString root_hash, Map<ByteString, ByteString> updates)
  {
    TrieDBBuffered db = new TrieDBBuffered(basedb);
    TrieNode root = db.load(root_hash);
    Assert.assertNotNull("Simluating merge from " + HashUtils.getHexString(root_hash), root);
    ByteString answer = mergeNode(db, root, updates).getHash();
    return answer;
  }

  /**
   * get entry from the given root hash or null of it does not exist
   */
  public ByteString getLeafData(ByteString root_hash, ByteString key)
  {
    TrieNode node = basedb.load(root_hash);
    if (node == null)
    {
      throw new RuntimeException(String.format("Referenced node %s not in database", HashUtils.getHexString(root_hash)));
    }
    if (node.getPrefix().equals(key))
    {
      if (node.getIsLeaf())
      {
        return node.getLeafData();
      }
      return null;
    }
    Assert.assertTrue(key.startsWith(node.getPrefix()));

    for(ChildEntry ce : node.getChildrenList())
    {
      ByteString p = node.getPrefix().concat(ce.getKey());
      if (key.startsWith(p))
      {
        return getLeafData(ce.getHash(), key);
      }
    }

    return null;
  }

  public TreeMap<ByteString, ByteString> getDataMap(ByteString hash, ByteString key, int max_results)
  {
    LinkedList<TrieNode> proof = new LinkedList<>();
    LinkedList<TrieNode> results = new LinkedList<>();

    getNodeDetails(hash, key, proof, results, max_results);
    
    TreeMap<ByteString, ByteString> map = new TreeMap<>(new ByteStringComparator());

    for(TrieNode node : results)
    {
      if (node.getIsLeaf())
      {
        map.put(node.getPrefix(), node.getLeafData());
      }
    }

    return map;
  }

  public void getNodeDetails(ByteString hash, ByteString target_key, LinkedList<TrieNode> proof, LinkedList<TrieNode> results, int max_results)
  {
    TrieNode node = basedb.load(hash);
    if (node == null)
    {
      throw new RuntimeException(String.format("Referenced node %s not in database", HashUtils.getHexString(hash)));
    }

    if (target_key.size() > node.getPrefix().size())
    {
      proof.add(node);
    }
    else
    {
      results.add(node);
    }
    if (results.size() >= max_results) return;


    for(ChildEntry ce : node.getChildrenList())
    {
      ByteString p = node.getPrefix().concat(ce.getKey());
      if (p.size() <= target_key.size())
      {
        if (target_key.startsWith(p))
        {
          getNodeDetails(ce.getHash(), target_key, proof, results, max_results);
        }
      }
      else
      {
        if(p.startsWith(target_key))
        {
          getNodeDetails(ce.getHash(), target_key, proof, results, max_results);
        }
      }
    }

 
  }

  private TrieNode mergeNode(TrieDB db, TrieNode node, Map<ByteString, ByteString> updates)
  {
    Assert.assertNotNull(node);
    //System.out.println("Doing merge on " + HashUtils.getHexString(node.getPrefix()) + " with " + updates.size());
    for(ByteString key : updates.keySet())
    {
      Assert.assertTrue("Prefix: " 
        + new String(node.getPrefix().toByteArray()) 
        + " key: " 
        + new String(key.toByteArray()) , 
        key.startsWith(node.getPrefix()));
    }
    TrieNode.Builder builder = TrieNode.newBuilder();
    builder.setPrefix(node.getPrefix());
    builder.setIsLeaf(node.getIsLeaf());
    if (node.getIsLeaf())
    {
      builder.setLeafData(node.getLeafData());
    }

    ArrayList<ByteString> hash_list = new ArrayList<>();

    hash_list.add(node.getPrefix());

    // Base case - at keylen
    //if (node.getIsLeaf())
    //if (node.getPrefix().size() == keylen)
    if (updates.containsKey(node.getPrefix()))
    {
      ByteString data = updates.get(node.getPrefix());
      if (data == null)
      {
        builder.setIsLeaf(false);
        builder.clearLeafData();
      }
      else
      {
        builder.setIsLeaf(true);
        builder.setLeafData(data);
      }

      //TrieNode newNode = builder.build();
      //db.save(newNode);
      //return newNode;
    }
    if (builder.getIsLeaf())
    {
      if (end_cap_data) hash_list.add(DATA_START);
      hash_list.add(builder.getLeafData());
      if (end_cap_data) hash_list.add(DATA_END);
    }

    //Intermediate node
    SetMultimap<ByteString, ByteString> fings = MultimapBuilder.hashKeys().hashSetValues().build();
    SetMultimap<ByteString, ByteString> changes_by_start = MultimapBuilder.hashKeys().hashSetValues().build();

    HashMap<ByteString, ChildEntry> children_by_start = new HashMap<>();

    TrieNode last_seen_child_node = null;

    int prefix_len = node.getPrefix().size();
    for(ByteString bs : updates.keySet())
    {
      if (bs.size() > prefix_len)
      {
        ByteString start = bs.substring(prefix_len, prefix_len + 1);
        ByteString rest = bs.substring(prefix_len);
        fings.put(start, rest);
        changes_by_start.put(start, bs);
      }
    }

    Map<ByteString, ChildEntry> children = new HashMap<>();
    for(ChildEntry c : node.getChildrenList())
    {
      ByteString bs = c.getKey();
      children.put(bs, c);

      ByteString start = bs.substring(0,1);
      fings.put(start, bs);
      children_by_start.put(start, c);
    }

    for(ByteString start : fings.keySet())
    {
      Set<ByteString> changes = changes_by_start.get(start);

      Set<ByteString> rests = fings.get(start);
      ByteString prefix_for_group = findLongestCommonStart(rests);

      if ((changes.isEmpty()) && (children.containsKey(prefix_for_group)))
      {
        builder.addChildren( children.get(prefix_for_group));
        continue; //woo ugly
      }

      Assert.assertTrue(prefix_for_group.size() > 0);

      TrieNode child_node = null;
      if (!children.containsKey(prefix_for_group))
      { 
        // Make a new node, put children if any under it

        TrieNode.Builder sub_builder = TrieNode.newBuilder();
        Assert.assertTrue(prefix_for_group.size() > 0);
        sub_builder.setPrefix(node.getPrefix().concat(prefix_for_group));
        Assert.assertTrue(sub_builder.getPrefix().size() > 0);
        if (children_by_start.get(start) != null)
        {
          ChildEntry ce = children_by_start.get(start);
          {
            int cut_len= prefix_for_group.size();
            ByteString new_prefix = ce.getKey().substring(cut_len);
            sub_builder.addChildren(ChildEntry.newBuilder().setKey(new_prefix).setHash(ce.getHash()).build());
          }
          //sub_builder.addChildren(children_by_start.get(start));
        }
        child_node = sub_builder.build();
        db.save(child_node);
        Assert.assertTrue(child_node.getPrefix().size() > 0);

        Assert.assertFalse(changes.isEmpty());

      }
      else
      {
        if (child_node == null)
        {
          child_node = db.load(children.get(prefix_for_group).getHash());
          //child_node = db.load(node.getPrefix().concat(prefix_for_group));
        }
      }
      
      Map<ByteString, ByteString> sub_updates = new HashMap<>();
      for(ByteString bs : changes)
      {
        sub_updates.put(bs, updates.get(bs));
      }
      child_node = mergeNode(db, child_node, sub_updates);
      if (child_node != null)
      {
        // The child node might have a different prefix
        // than what we expect, since it might need to structural shorten itself

        ByteString prefix_for_child_node = child_node.getPrefix().substring(node.getPrefix().size());
        Assert.assertTrue(""+start, child_node.getPrefix().size() > 0);
        Assert.assertTrue(""+start, prefix_for_child_node.size() > 0);
        builder.addChildren( ChildEntry.newBuilder().setKey(prefix_for_child_node).setHash(child_node.getHash()));

        last_seen_child_node = child_node;
      }
      
    }

    if (!builder.getIsLeaf())
    {
      if (node.getPrefix().size() > 0)
      {
        if (builder.getChildrenCount() == 0)
        {
          return null;
        }
        if (builder.getChildrenCount() == 1)
        {
          if (last_seen_child_node != null)
          {
            return last_seen_child_node;
          }
          TrieNode child_node = db.load(builder.getChildrenList().get(0).getHash());
          return child_node;
        }
      }
    }

    //ok, we should have all the dumb child nodes in place, now it is time to redo the hashes

    TreeMap<ByteString, ChildEntry> sortedChildren = new TreeMap<>(new ByteStringComparator());

    for(ChildEntry ce : builder.getChildrenList())
    {
      sortedChildren.put(ce.getKey(), ce);
    }
    for(ChildEntry ce : sortedChildren.values())
    {
      hash_list.add(ce.getKey());
      hash_list.add(ce.getHash());
    }

    builder.setHash(HashUtils.hashConcat( hash_list ));

    TrieNode new_node = builder.build();

    db.save(new_node);

    return new_node;

  }

  public void printTree(ByteString root)
  {
    printNode(basedb, root, 0);
  }

  private void printNode(TrieDB db, ByteString hash, int indent)
  {
    String spaces = "";
    while(spaces.length() < indent) spaces += " ";

    TrieNode node = db.load(hash);
    Assert.assertNotNull("Loading node: " + HashUtils.getHexString(hash), node);

    System.out.println(spaces + "node:" + HashUtils.getHexString(node.getPrefix()) + " - " +node.getChildrenCount());

    for(ChildEntry ce : node.getChildrenList())
    {
      printNode(db, ce.getHash(), indent+2);
    }
    if (node.getIsLeaf())
    {
      System.out.println(spaces + " data - " + HashUtils.getHexString(node.getLeafData()));
    }

  }

  /**
   * Pretty slow, use for diagnostics only
   */
  public TrieReport getTreeReport(ByteString root)
  {
    TrieReport report = new TrieReport();

    getTreeReportInternal(basedb, report, root);

    return report;

  }

  public void assertValid(ByteString root)
  {
    TrieReport report = getTreeReport(root);

    System.out.println(report);
    Assert.assertEquals(report.toString(), 0, report.malformed_node);
  }

  private void getTreeReportInternal(TrieDB db, TrieReport report, ByteString hash)
  {
    TrieNode node = db.load(hash);
    report.nodes++;

    int children = 0;
    int leaf = 0;

    LinkedList<ByteString> hash_list = new LinkedList<>();

    hash_list.add(node.getPrefix());

    if (node.getIsLeaf())
    {
      report.leaf_data_count++;
      report.leaf_data_size+=node.getLeafData().size();
      leaf++;
      if (end_cap_data) hash_list.add(DATA_START);
      hash_list.add(node.getLeafData());
      if (end_cap_data) hash_list.add(DATA_END);
    }


    HashSet<ByteString> start_bytes = new HashSet<>();
    for(ChildEntry ce : node.getChildrenList())
    {
      getTreeReportInternal(db, report, ce.getHash());
      children++;
      ByteString first = ce.getKey().substring(0,1);
      start_bytes.add(first);
    }
    if (start_bytes.size() < children)
    {
      report.malformed_node++;
      report.malformed_node_shared_prefix++;
    }

    if (node.getPrefix().size() > 0)
    {
      if ((children == 0) && (leaf == 0))
      {
        report.malformed_node++;
        report.malformed_node_dead_end++;
      }
      if ((leaf==0) && (children == 1))
      {
        report.malformed_node++;
        report.malformed_node_pass_through++;
      }

    }

    TreeMap<ByteString, ChildEntry> sortedChildren = new TreeMap<>(new ByteStringComparator());

    for(ChildEntry ce : node.getChildrenList())
    {
      sortedChildren.put(ce.getKey(), ce);
    }
    for(ChildEntry ce : sortedChildren.values())
    {
      hash_list.add(ce.getKey());
      hash_list.add(ce.getHash());
    }



    ByteString found_hash = HashUtils.hashConcat(hash_list);
    if (!found_hash.equals(hash))
    {
      report.malformed_node++;
      report.malformed_node_hash++;
    }
  }

  public static ByteString findLongestCommonStart(Set<ByteString> rests)
  {
    ByteString longest = null;
    for(ByteString bs : rests)
    {
      if (longest == null) longest = bs;
      else
      {
        int sz = Math.min(longest.size(), bs.size());
        int match = 0;
        for(int i=0; i<sz; i++)
        {
          if (longest.byteAt(i) == bs.byteAt(i))
          {
            match = i;
          }
          else break;
        }
        longest = bs.substring(0, match +1);
      }
    }


    return longest;

  }

}
