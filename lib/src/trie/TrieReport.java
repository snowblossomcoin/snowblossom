package snowblossom.lib.trie;

public class TrieReport
{
  long nodes;
  long leaf_data_count;
  long leaf_data_size;

  long malformed_node;
  long malformed_node_dead_end;
  long malformed_node_pass_through;
  long malformed_node_shared_prefix;
  long malformed_node_hash;

  public String toString()
  {
    return String.format("Report{nodes:%d leafs:%d (%d) malforms:%d (deadend:%d passthru:%d prefix:%d hash:%d)}", 
      nodes, leaf_data_count, leaf_data_size, 
      malformed_node, malformed_node_dead_end, malformed_node_pass_through,
      malformed_node_shared_prefix,
      malformed_node_hash);
  }
}
