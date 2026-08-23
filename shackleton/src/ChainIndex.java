package snowblossom.shackleton;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import duckutil.PeriodicThread;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.proto.*;

/**
 * Background thread that keeps an in-memory picture of the recent chain state
 * for all shards: recent blocks per shard, per-shard and network statistics
 * and the shard tree (which shards exist, where they split).
 *
 * Web requests read from this index and never have to walk the chain themselves.
 */
public class ChainIndex extends PeriodicThread
{
  private static final Logger logger = Logger.getLogger("snowblossom.shackleton");

  public static final long HOUR_MS = 3600L * 1000L;
  public static final long DAY_MS = 24L * HOUR_MS;

  private final Shackleton shackleton;
  private final int depth;

  private volatile ImmutableMap<Integer, ImmutableList<BlockInfo>> shard_recent = ImmutableMap.of();
  private volatile ImmutableList<BlockInfo> recent_all = ImmutableList.of();
  private volatile ImmutableMap<Integer, ShardStats> shard_stats = ImmutableMap.of();
  private volatile NetStats net_stats = null;
  private volatile ShardTree shard_tree = null;
  private volatile boolean ready = false;
  private volatile long last_update = 0;
  private volatile String last_error = null;

  public ChainIndex(Shackleton shackleton, int depth)
  {
    super(5000L);
    this.shackleton = shackleton;
    this.depth = Math.max(50, depth);
    setName("ChainIndex");
    setDaemon(true);
  }

  public boolean isReady() { return ready; }
  public long getLastUpdate() { return last_update; }
  public String getLastError() { return last_error; }
  public int getDepth() { return depth; }

  public ImmutableList<BlockInfo> getRecent(int shard)
  {
    ImmutableList<BlockInfo> l = shard_recent.get(shard);
    if (l == null) return ImmutableList.of();
    return l;
  }

  public ImmutableList<BlockInfo> getRecentAll() { return recent_all; }
  public ImmutableMap<Integer, ShardStats> getShardStats() { return shard_stats; }
  public ShardStats getShardStats(int shard) { return shard_stats.get(shard); }
  public NetStats getNetStats() { return net_stats; }
  public ShardTree getShardTree() { return shard_tree; }

  /** Best known height for a shard, including retired shards (highest descendant) */
  public int getHeightForConfirmations(int shard)
  {
    ShardStats ss = shard_stats.get(shard);
    if (ss != null) return ss.height;
    ShardTree tree = shard_tree;
    if (tree != null)
    {
      ShardNode n = tree.nodes.get(shard);
      if (n != null)
      {
        int best = n.last_height;
        for (int c : n.children)
        {
          best = Math.max(best, getHeightForConfirmations(c));
        }
        return best;
      }
    }
    return -1;
  }

  @Override
  public void runPass() throws Exception
  {
    try
    {
      ChainCache cache = shackleton.getCache();
      NodeStatus ns = cache.getNodeStatus();
      Map<Integer, BlockHeader> heads = cache.getNetShardHeads(ns);
      Set<Integer> building = new TreeSet<>(ns.getShardHeadMap().keySet());
      if (building.size() == 0) building.addAll(heads.keySet());

      // 1) recent block lists per shard
      TreeMap<Integer, ImmutableList<BlockInfo>> new_recent = new TreeMap<>();
      for (Map.Entry<Integer, BlockHeader> me : heads.entrySet())
      {
        int shard = me.getKey();
        new_recent.put(shard, updateShardList(shard, me.getValue(), shard_recent.get(shard)));
      }
      ImmutableMap<Integer, ImmutableList<BlockInfo>> new_recent_im = ImmutableMap.copyOf(new_recent);

      // 2) merged list
      ArrayList<BlockInfo> all = new ArrayList<>();
      HashSet<ChainHash> seen = new HashSet<>();
      for (ImmutableList<BlockInfo> lst : new_recent.values())
      {
        for (BlockInfo bi : lst)
        {
          if (seen.add(bi.hash)) all.add(bi);
        }
      }
      Collections.sort(all, new Comparator<BlockInfo>()
      {
        public int compare(BlockInfo a, BlockInfo b)
        {
          if (a.timestamp != b.timestamp) return Long.compare(b.timestamp, a.timestamp);
          if (a.height != b.height) return Integer.compare(b.height, a.height);
          return Integer.compare(a.shard, b.shard);
        }
      });
      if (all.size() > depth * 2) all = new ArrayList<>(all.subList(0, depth * 2));

      // 3) shard tree
      ShardTree tree = buildTree(heads, building);

      // 4) per shard stats
      TreeMap<Integer, ShardStats> stats = new TreeMap<>();
      for (Map.Entry<Integer, BlockHeader> me : heads.entrySet())
      {
        int shard = me.getKey();
        ShardStats ss = computeShardStats(shard, me.getValue(), new_recent.get(shard), building.contains(shard), tree);
        if (ss != null) stats.put(shard, ss);
      }

      // 5) network stats
      NetStats nstat = computeNetStats(ns, stats, tree);

      shard_recent = new_recent_im;
      recent_all = ImmutableList.copyOf(all);
      shard_tree = tree;
      shard_stats = ImmutableMap.copyOf(stats);
      net_stats = nstat;
      last_update = System.currentTimeMillis();
      last_error = null;
      if (!ready)
      {
        logger.info("ChainIndex ready: " + stats.keySet() + " shards, " + all.size() + " recent blocks");
      }
      ready = true;
    }
    catch (Throwable t)
    {
      last_error = t.toString();
      logger.log(Level.WARNING, "ChainIndex pass failed: " + t, t);
    }
  }

  private ImmutableList<BlockInfo> updateShardList(int shard, BlockHeader head, ImmutableList<BlockInfo> old)
  {
    ChainCache cache = shackleton.getCache();
    ChainHash head_hash = new ChainHash(head.getSnowHash());

    if ((old != null) && (old.size() > 0) && old.get(0).hash.equals(head_hash))
    {
      return old;
    }

    HashMap<ChainHash, Integer> old_index = new HashMap<>();
    if (old != null)
    {
      for (int i = 0; i < old.size(); i++) old_index.put(old.get(i).hash, i);
    }

    LinkedList<BlockInfo> fresh = new LinkedList<>();
    ChainHash cur = head_hash;
    int join = -1;
    int steps = 0;
    while ((cur != null) && (steps < depth))
    {
      Integer j = old_index.get(cur);
      if (j != null)
      {
        join = j;
        break;
      }
      BlockInfo bi = cache.getBlockInfo(cur);
      if (bi == null) break;
      if (bi.shard != shard) break; // walked past the start of this shard into the parent
      fresh.add(bi);
      steps++;
      if (bi.height == 0) break;
      cur = bi.prev;
    }

    ArrayList<BlockInfo> merged = new ArrayList<>(fresh);
    if ((join >= 0) && (old != null))
    {
      merged.addAll(old.subList(join, old.size()));
    }
    if (merged.size() > depth) merged = new ArrayList<>(merged.subList(0, depth));
    return ImmutableList.copyOf(merged);
  }

  private ShardStats computeShardStats(int shard, BlockHeader head, ImmutableList<BlockInfo> list, boolean building, ShardTree tree)
  {
    ChainCache cache = shackleton.getCache();
    NetworkParams params = shackleton.getParams();
    ChainHash head_hash = new ChainHash(head.getSnowHash());
    BlockSummary summary = cache.getSummary(head_hash);
    if (summary == null) return null;
    BlockInfo head_info = (list.size() > 0) ? list.get(0) : cache.getBlockInfo(head_hash);
    if (head_info == null) return null;

    ShardStats ss = new ShardStats();
    ss.shard = shard;
    ss.head = head;
    ss.summary = summary;
    ss.head_info = head_info;
    ss.height = head.getBlockHeight();
    ss.timestamp = head.getTimestamp();
    ss.building = building;
    ss.coordinator = BlockchainUtil.isCoordinator(shard);
    ss.generation = ShardUtil.getShardGeneration(shard);
    ss.blocktime_avg_ms = summary.getBlocktimeAverageMs();
    ss.activated_field = summary.getActivatedField();
    ss.tx_size_average = summary.getTxSizeAverage();
    ss.shard_length = summary.getShardLength();
    ss.total_transactions = summary.getTotalTransactions();
    ss.work_sum = summary.getWorkSum();
    ss.target_diff = PowUtil.getDiffForTarget(BlockchainUtil.targetBytesToBigInteger(head.getTarget()));
    ss.avg_diff = PowUtil.getDiffForTarget(BlockchainUtil.readInteger(summary.getTargetAverage()));
    double bt_sec = Math.max(1.0, ss.blocktime_avg_ms / 1000.0);
    ss.hashrate = Math.pow(2.0, ss.avg_diff) / bt_sec;
    ss.subsidy = head_info.subsidy;
    ss.split_size_ratio = (double) ss.tx_size_average / (double) params.getShardForkThreshold();
    ss.split_len_ratio = (double) ss.shard_length / (double) params.getMinShardLength();
    ss.can_split = ShardUtil.getShardChildIdRight(shard) <= params.getMaxShardId();

    long now = System.currentTimeMillis();
    long since_24h = now - DAY_MS;
    long since_1h = now - HOUR_MS;
    boolean covered = false;
    BlockInfo oldest = null;
    for (BlockInfo bi : list)
    {
      if (bi.timestamp < since_24h)
      {
        covered = true;
        break;
      }
      oldest = bi;
      ss.blocks_24h++;
      ss.tx_24h += bi.tx_count;
      ss.fees_24h += bi.fees;
      ss.coinbase_24h += bi.coinbase_total;
      ss.size_24h += bi.size;
      if (bi.timestamp >= since_1h)
      {
        ss.blocks_1h++;
        ss.tx_1h += bi.tx_count;
      }
    }
    if ((!covered) && (oldest != null) && (oldest.height > 0))
    {
      // extend the 24h window with headers only
      BlockHeader h = cache.getHeader(oldest.prev);
      int steps = 0;
      while ((h != null) && (h.getTimestamp() >= since_24h) && (steps < 5000) && (h.getShardId() == shard))
      {
        ss.blocks_24h++;
        ss.tx_24h += h.getTxCount();
        if (h.getTimestamp() >= since_1h)
        {
          ss.blocks_1h++;
          ss.tx_1h += h.getTxCount();
        }
        if (h.getBlockHeight() == 0) break;
        h = cache.getHeader(new ChainHash(h.getPrevBlockHash()));
        steps++;
      }
    }

    if (tree != null)
    {
      ShardNode n = tree.nodes.get(shard);
      if (n != null)
      {
        ss.first_height = n.first_height;
        ss.parent = n.parent;
        ss.children = n.children;
      }
    }
    return ss;
  }

  private NetStats computeNetStats(NodeStatus ns, Map<Integer, ShardStats> stats, ShardTree tree)
  {
    NetworkParams params = shackleton.getParams();
    NetStats n = new NetStats();
    n.updated = System.currentTimeMillis();
    n.mem_pool_size = ns.getMemPoolSize();
    n.connected_peers = ns.getConnectedPeers();
    n.estimated_nodes = ns.getEstimatedNodes();
    n.node_version = ns.getNodeVersion();
    n.version_map = ImmutableMap.copyOf(new TreeMap<String, Integer>(ns.getVersionMap()));
    n.shard_count = stats.size();

    double diff_sum = 0;
    long bt_sum = 0;
    int bt_count = 0;
    int field = 0;
    ShardStats field_src = null;
    for (ShardStats ss : stats.values())
    {
      n.max_height = Math.max(n.max_height, ss.height);
      n.hashrate += ss.hashrate;
      diff_sum += ss.avg_diff;
      bt_sum += ss.blocktime_avg_ms;
      bt_count++;
      n.blocks_1h += ss.blocks_1h;
      n.tx_1h += ss.tx_1h;
      n.blocks_24h += ss.blocks_24h;
      n.tx_24h += ss.tx_24h;
      n.fees_24h += ss.fees_24h;
      n.coinbase_24h += ss.coinbase_24h;
      n.size_24h += ss.size_24h;
      if ((field_src == null) || (ss.activated_field > field))
      {
        field = ss.activated_field;
        field_src = ss;
      }
      n.newest_block_ts = Math.max(n.newest_block_ts, ss.timestamp);
    }
    if (bt_count > 0)
    {
      n.avg_diff = diff_sum / bt_count;
      n.blocktime_avg_ms = bt_sum / bt_count;
    }

    n.activated_field = field;
    SnowFieldInfo sf = params.getSnowFieldInfo(field);
    SnowFieldInfo next_sf = params.getSnowFieldInfo(field + 1);
    n.field_name = (sf == null) ? ("field " + field) : sf.getName();
    if ((sf != null) && (next_sf != null) && (field_src != null))
    {
      double prev_diff = PowUtil.getDiffForTarget(sf.getActivationTarget());
      double next_diff = PowUtil.getDiffForTarget(next_sf.getActivationTarget());
      n.next_field_name = next_sf.getName();
      n.next_field_diff = next_diff;
      n.field_activation_diff = prev_diff;
      n.field_progress = Math.max(0.0, Math.min(1.0, (field_src.avg_diff - prev_diff) / (next_diff - prev_diff)));
      n.field_hashrate_ratio = Math.pow(2.0, field_src.avg_diff - next_diff);
      n.field_hashrate_factor = Math.pow(2.0, next_diff - field_src.avg_diff);
    }
    else
    {
      n.next_field_name = null;
      n.field_progress = 0;
      n.field_hashrate_ratio = 0;
      n.field_hashrate_factor = 0;
    }

    // total transactions across the shard tree
    long total_tx = 0;
    if (tree != null)
    {
      for (ShardNode node : tree.nodes.values())
      {
        ShardStats ss = stats.get(node.id);
        int known_children = 0;
        for (int c : node.children) if (tree.nodes.containsKey(c)) known_children++;
        if (known_children == 0)
        {
          if (ss != null) total_tx += ss.total_transactions;
        }
        else
        {
          if (node.total_tx_at_end >= 0) total_tx -= node.total_tx_at_end * (known_children - 1);
        }
      }
    }
    if (total_tx <= 0)
    {
      for (ShardStats ss : stats.values()) total_tx = Math.max(total_tx, ss.total_transactions);
    }
    n.total_transactions = total_tx;
    n.subsidy_per_height = PowUtil.getBlockReward(params, n.max_height);
    return n;
  }

  private ShardTree buildTree(Map<Integer, BlockHeader> heads, Set<Integer> building)
  {
    ChainCache cache = shackleton.getCache();
    ShardTree old = shard_tree;
    TreeMap<Integer, ShardNode> nodes = new TreeMap<>();

    LinkedList<Integer> queue = new LinkedList<>();
    for (Map.Entry<Integer, BlockHeader> me : heads.entrySet())
    {
      int s = me.getKey();
      ShardNode n = new ShardNode(s);
      n.active = true;
      n.building = building.contains(s);
      n.head = me.getValue();
      n.last_height = n.head.getBlockHeight();
      n.last_hash = new ChainHash(n.head.getSnowHash());
      nodes.put(s, n);
      queue.add(s);
    }

    while (queue.size() > 0)
    {
      int s = queue.poll();
      ShardNode n = nodes.get(s);
      if (n == null) continue;
      if (n.first_height >= 0) continue;

      // Reuse what we learned previously for this shard (first block never changes)
      ShardNode prev_node = (old == null) ? null : old.nodes.get(s);
      if ((prev_node != null) && (prev_node.first_height >= 0) && (prev_node.first_hash != null))
      {
        n.first_height = prev_node.first_height;
        n.first_hash = prev_node.first_hash;
        n.first_ts = prev_node.first_ts;
      }
      else if (s == 0)
      {
        n.first_height = 0;
        BlockHeader g = cache.getHeaderAtHeight(0, 0);
        if (g != null)
        {
          n.first_hash = new ChainHash(g.getSnowHash());
          n.first_ts = g.getTimestamp();
        }
      }
      else
      {
        BlockSummary sum = cache.getSummary(n.last_hash);
        if (sum != null)
        {
          int first = n.last_height - sum.getShardLength() + 1;
          BlockHeader fh = cache.getHeaderAtHeight(s, first);
          if ((fh != null) && (fh.getShardId() == s))
          {
            n.first_height = first;
            n.first_hash = new ChainHash(fh.getSnowHash());
            n.first_ts = fh.getTimestamp();
          }
          else
          {
            n.first_height = first;
          }
        }
      }

      if (s > 0)
      {
        int p = ShardUtil.getShardParentId(s);
        ShardNode pn = nodes.get(p);
        if (pn == null)
        {
          pn = new ShardNode(p);
          nodes.put(p, pn);
        }
        pn.children.add(s);
        // parent's last block is the prev of our first block
        if ((n.first_hash != null) && (!pn.active))
        {
          BlockHeader fh = cache.getHeader(n.first_hash);
          if (fh != null)
          {
            BlockHeader ph = cache.getHeader(new ChainHash(fh.getPrevBlockHash()));
            if ((ph != null) && (ph.getShardId() == p))
            {
              if (pn.last_height < ph.getBlockHeight())
              {
                pn.last_height = ph.getBlockHeight();
                pn.last_hash = new ChainHash(ph.getSnowHash());
                pn.head = ph;
              }
            }
          }
        }
        queue.add(p);
      }
    }

    // totals at end for split shards (needed for total transaction accounting)
    for (ShardNode n : nodes.values())
    {
      if ((n.children.size() > 0) && (n.last_hash != null))
      {
        BlockSummary sum = cache.getSummary(n.last_hash);
        if (sum != null) n.total_tx_at_end = sum.getTotalTransactions();
      }
      if (n.head != null) n.last_ts = n.head.getTimestamp();
    }

    ArrayList<Integer> leaves = new ArrayList<>();
    int max_gen = 0;
    for (ShardNode n : nodes.values())
    {
      n.generation = ShardUtil.getShardGeneration(n.id);
      n.parent = (n.id == 0) ? -1 : ShardUtil.getShardParentId(n.id);
      max_gen = Math.max(max_gen, n.generation);
      if (n.active) leaves.add(n.id);
    }
    return new ShardTree(nodes, leaves, max_gen);
  }

  // -------------------------------------------------------------
  // Data classes
  // -------------------------------------------------------------
  public static class ShardStats
  {
    public int shard;
    public BlockHeader head;
    public BlockSummary summary;
    public BlockInfo head_info;
    public int height;
    public long timestamp;
    public boolean building;
    public boolean coordinator;
    public int generation;
    public int parent = -1;
    public int first_height = -1;
    public Set<Integer> children = new TreeSet<>();
    public int blocks_1h, tx_1h, blocks_24h, tx_24h;
    public long fees_24h, coinbase_24h, size_24h;
    public double target_diff, avg_diff, hashrate;
    public long blocktime_avg_ms;
    public int activated_field;
    public long tx_size_average;
    public int shard_length;
    public long total_transactions;
    public String work_sum;
    public long subsidy;
    public double split_size_ratio;
    public double split_len_ratio;
    public boolean can_split;

    public long getAgeMs() { return System.currentTimeMillis() - timestamp; }
    public boolean isSplitReady() { return can_split && (split_size_ratio >= 1.0) && (split_len_ratio >= 1.0); }
  }

  public static class NetStats
  {
    public long updated;
    public int shard_count;
    public int max_height;
    public double hashrate;
    public double avg_diff;
    public long blocktime_avg_ms;
    public int blocks_1h, tx_1h, blocks_24h, tx_24h;
    public long fees_24h, coinbase_24h, size_24h;
    public long total_transactions;
    public int activated_field;
    public String field_name;
    public String next_field_name;
    public double next_field_diff;
    public double field_progress;        // between the current field's activation difficulty and the next one's (0 if below)
    public double field_hashrate_ratio;  // current hashrate as a fraction of the hashrate needed for the next field
    public double field_hashrate_factor; // how many times the current hashrate is needed to reach the next field
    public double field_activation_diff;
    public int mem_pool_size, connected_peers, estimated_nodes;
    public String node_version;
    public ImmutableMap<String, Integer> version_map = ImmutableMap.of();
    public long subsidy_per_height;
    public long newest_block_ts;
  }

  public static class ShardNode
  {
    public final int id;
    public int parent = -1;
    public int generation;
    public boolean active;
    public boolean building;
    public BlockHeader head;
    public int first_height = -1;
    public ChainHash first_hash;
    public long first_ts;
    public int last_height = -1;
    public ChainHash last_hash;
    public long last_ts;
    public long total_tx_at_end = -1;
    public final TreeSet<Integer> children = new TreeSet<>();

    public ShardNode(int id) { this.id = id; }

    public boolean isRetired() { return !active && (children.size() > 0); }
    public int getBlockCount()
    {
      if ((first_height < 0) || (last_height < 0)) return -1;
      return last_height - first_height + 1;
    }
  }

  public static class ShardTree
  {
    public final ImmutableMap<Integer, ShardNode> nodes;
    public final ImmutableList<Integer> leaves;
    public final int max_generation;

    public ShardTree(Map<Integer, ShardNode> nodes, List<Integer> leaves, int max_generation)
    {
      this.nodes = ImmutableMap.copyOf(nodes);
      this.leaves = ImmutableList.copyOf(leaves);
      this.max_generation = max_generation;
    }
  }
}
