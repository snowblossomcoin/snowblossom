package snowblossom.shackleton;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import duckutil.PeriodicThread;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.proto.*;

/**
 * Periodically walks the last N blocks of every active shard and tallies
 * miner addresses, coinbase remarks (pools) and motion votes.
 */
public class MiningStats extends PeriodicThread
{
  private static final Logger logger = Logger.getLogger("snowblossom.shackleton");

  private final Shackleton shackleton;
  private final int look_back;
  private volatile Snapshot snapshot = null;
  private volatile boolean running = false;

  public MiningStats(Shackleton shackleton, int look_back)
  {
    super(60000L);
    this.shackleton = shackleton;
    this.look_back = Math.max(10, look_back);
    setName("MiningStats");
    setDaemon(true);
  }

  public Snapshot getSnapshot() { return snapshot; }
  public int getLookBack() { return look_back; }
  public boolean isRunning() { return running; }

  @Override
  public void runPass() throws Exception
  {
    running = true;
    try
    {
      // the chain index fills the block cache with the most recent blocks, wait for it
      while (!shackleton.getIndex().isReady()) Thread.sleep(2000L);
      ChainCache cache = shackleton.getCache();
      NodeStatus ns = cache.getNodeStatus();
      Map<Integer, BlockHeader> heads = cache.getNetShardHeads(ns);

      HashSet<ChainHash> visited = new HashSet<>();
      HashMap<String, Tally> remarks = new HashMap<>();
      HashMap<String, Tally> miners = new HashMap<>();
      TreeMap<Integer, VoteCount> votes = new TreeMap<>();
      TreeMap<Integer, Integer> per_shard = new TreeMap<>();
      long total_reward = 0;
      long total_fees = 0;
      long oldest = Long.MAX_VALUE;
      long newest = 0;
      int blocks = 0;

      for (Map.Entry<Integer, BlockHeader> me : heads.entrySet())
      {
        int shard = me.getKey();
        ChainHash cur = new ChainHash(me.getValue().getSnowHash());
        int steps = 0;
        while ((cur != null) && (steps < look_back))
        {
          if (!visited.add(cur)) break;
          BlockInfo bi = cache.getBlockInfo(cur);
          if (bi == null) break;
          blocks++;
          steps++;
          per_shard.put(bi.shard, per_shard.getOrDefault(bi.shard, 0) + 1);
          total_reward += bi.coinbase_total;
          total_fees += bi.fees;
          oldest = Math.min(oldest, bi.timestamp);
          newest = Math.max(newest, bi.timestamp);

          String remark = bi.remark;
          if (remark.length() == 0) remark = "(no remark)";
          tally(remarks, remark, bi);
          if (bi.miner_addr.length() > 0) tally(miners, bi.miner_addr, bi);
          updateVotes(votes, bi);

          if (bi.height == 0) break;
          cur = bi.prev;
        }
      }

      if (blocks == 0) return;

      Snapshot s = new Snapshot();
      s.generated = System.currentTimeMillis();
      s.blocks = blocks;
      s.look_back = look_back;
      s.oldest_ts = oldest;
      s.newest_ts = newest;
      s.total_reward = total_reward;
      s.total_fees = total_fees;
      s.remarks = sortEntries(remarks, blocks, total_reward);
      s.miners = sortEntries(miners, blocks, total_reward);
      s.votes = ImmutableMap.copyOf(votes);
      s.blocks_per_shard = ImmutableMap.copyOf(per_shard);
      snapshot = s;
    }
    catch (Throwable t)
    {
      logger.log(Level.WARNING, "MiningStats pass failed: " + t, t);
    }
    finally
    {
      running = false;
    }
  }

  private static void tally(HashMap<String, Tally> map, String key, BlockInfo bi)
  {
    Tally t = map.get(key);
    if (t == null)
    {
      t = new Tally();
      map.put(key, t);
    }
    t.blocks++;
    t.reward += bi.coinbase_total;
    t.shards.add(bi.shard);
    t.last_ts = Math.max(t.last_ts, bi.timestamp);
    if (t.sample_addr == null) t.sample_addr = bi.miner_addr;
    if (bi.remark.length() > 0) t.remarks.add(bi.remark);
  }

  private static ImmutableList<Entry> sortEntries(HashMap<String, Tally> map, int blocks, long total_reward)
  {
    ArrayList<Entry> lst = new ArrayList<>();
    for (Map.Entry<String, Tally> me : map.entrySet())
    {
      Tally t = me.getValue();
      Entry e = new Entry();
      e.name = me.getKey();
      e.blocks = t.blocks;
      e.reward = t.reward;
      e.share = (blocks == 0) ? 0 : (double) t.blocks / (double) blocks;
      e.shards = ImmutableList.copyOf(t.shards);
      e.last_ts = t.last_ts;
      e.sample_addr = t.sample_addr;
      e.remarks = ImmutableList.copyOf(t.remarks);
      lst.add(e);
    }
    Collections.sort(lst, new Comparator<Entry>()
    {
      public int compare(Entry a, Entry b)
      {
        if (a.blocks != b.blocks) return Integer.compare(b.blocks, a.blocks);
        return a.name.compareTo(b.name);
      }
    });
    return ImmutableList.copyOf(lst);
  }

  private static void updateVotes(Map<Integer, VoteCount> vote_map, BlockInfo bi)
  {
    Set<Integer> app = new TreeSet<>(bi.motions_approved);
    Set<Integer> rej = new TreeSet<>(bi.motions_rejected);
    Set<Integer> all = new TreeSet<>();
    all.addAll(app);
    all.addAll(rej);
    for (int issue : all)
    {
      VoteCount vc = vote_map.get(issue);
      if (vc == null)
      {
        vc = new VoteCount();
        vote_map.put(issue, vc);
      }
      if (app.contains(issue) && rej.contains(issue)) vc.both++;
      else if (app.contains(issue)) vc.yes++;
      else vc.no++;
    }
  }

  private static class Tally
  {
    int blocks;
    long reward;
    long last_ts;
    String sample_addr;
    TreeSet<Integer> shards = new TreeSet<>();
    TreeSet<String> remarks = new TreeSet<>();
  }

  public static class Entry
  {
    public String name;
    public int blocks;
    public long reward;
    public double share;
    public ImmutableList<Integer> shards;
    public long last_ts;
    public String sample_addr;
    public ImmutableList<String> remarks;
  }

  public static class VoteCount
  {
    public int yes;
    public int no;
    public int both;
    public int total() { return yes + no + both; }
  }

  public static class Snapshot
  {
    public long generated;
    public int blocks;
    public int look_back;
    public long oldest_ts;
    public long newest_ts;
    public long total_reward;
    public long total_fees;
    public ImmutableList<Entry> remarks;
    public ImmutableList<Entry> miners;
    public ImmutableMap<Integer, VoteCount> votes;
    public ImmutableMap<Integer, Integer> blocks_per_shard;
  }
}
