package snowblossom.shackleton;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import duckutil.PeriodicThread;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.proto.*;

/**
 * Keeps a compact record of every block of the last N days (per shard, from headers only)
 * and buckets them on demand into arbitrary time ranges for the statistics page.
 * The record is updated incrementally: only headers not seen before are fetched.
 */
public class HistoryStats extends PeriodicThread
{
  private static final Logger logger = Logger.getLogger("snowblossom.shackleton");
  public static final long HOUR_MS = 3600L * 1000L;
  public static final long DAY_MS = 24L * HOUR_MS;

  /** One block */
  public static class Rec
  {
    public final ChainHash hash;
    public final int height;
    public final long ts;
    public final double diff;
    public final int txs;
    public final int tx_data;

    Rec(BlockHeader h)
    {
      hash = new ChainHash(h.getSnowHash());
      height = h.getBlockHeight();
      ts = h.getTimestamp();
      diff = PowUtil.getDiffForTarget(BlockchainUtil.targetBytesToBigInteger(h.getTarget()));
      txs = h.getTxCount();
      tx_data = h.getTxDataSizeSum();
    }
  }

  /** Aggregate over a time bucket */
  public static class Bucket
  {
    public final long start;
    public long length;   // actual covered time in ms (the last bucket is partial)
    public int blocks;
    public long txs;
    public double hashes;
    public double diff_sum;
    public long tx_data;

    public Bucket(long start, long length)
    {
      this.start = start;
      this.length = length;
    }

    public double avgDiff() { return (blocks == 0) ? 0 : diff_sum / blocks; }
    public double hashrate() { return hashes / Math.max(1, length / 1000.0); }
    public double avgBlockTimeSec() { return (blocks == 0) ? 0 : (length / 1000.0) / blocks; }
  }

  /** A bucketed view for one range */
  public static class Series
  {
    public long generated;
    public long range_ms;
    public long bucket_ms;
    public ImmutableList<Long> starts;
    public ImmutableList<Bucket> total;
    public ImmutableMap<Integer, ImmutableList<Bucket>> per_shard;
  }

  /** Named range presets: key -> {range ms, bucket ms, label} */
  public static final String[][] RANGES = new String[][]{
    {"24h", "" + DAY_MS, "" + HOUR_MS, "24 hours", "hourly"},
    {"7d", "" + (7 * DAY_MS), "" + (6 * HOUR_MS), "7 days", "6 hour"},
    {"30d", "" + (30 * DAY_MS), "" + DAY_MS, "30 days", "daily"},
    {"90d", "" + (90 * DAY_MS), "" + (3 * DAY_MS), "90 days", "3 day"},
    {"1y", "" + (365 * DAY_MS), "" + (7 * DAY_MS), "1 year", "weekly"},
  };

  public static String[] findRange(String key)
  {
    for (String[] r : RANGES) if (r[0].equals(key)) return r;
    return RANGES[2];
  }

  private final Shackleton shackleton;
  private final int days;
  // shard -> height -> record (only the current chain)
  private volatile ImmutableMap<Integer, ImmutableMap<Integer, Rec>> records = ImmutableMap.of();
  private volatile long generated = 0;
  private volatile boolean ready = false;

  public HistoryStats(Shackleton shackleton, int days)
  {
    super(5L * 60L * 1000L);
    this.shackleton = shackleton;
    this.days = Math.max(2, days);
    setName("HistoryStats");
    setDaemon(true);
  }

  public int getDays() { return days; }
  public boolean isReady() { return ready; }
  public long getGenerated() { return generated; }

  public int recordCount()
  {
    int n = 0;
    for (ImmutableMap<Integer, Rec> m : records.values()) n += m.size();
    return n;
  }

  @Override
  public void runPass() throws Exception
  {
    try
    {
      ChainCache cache = shackleton.getCache();
      ChainIndex index = shackleton.getIndex();
      while (!index.isReady()) Thread.sleep(2000L);

      long now = System.currentTimeMillis();
      long cutoff = now - (long) days * DAY_MS - DAY_MS;

      // mutable copy of the current records
      HashMap<Integer, TreeMap<Integer, Rec>> work = new HashMap<>();
      for (Map.Entry<Integer, ImmutableMap<Integer, Rec>> me : records.entrySet())
      {
        work.put(me.getKey(), new TreeMap<>(me.getValue()));
      }

      NodeStatus ns = cache.getNodeStatus();
      Map<Integer, BlockHeader> heads = cache.getNetShardHeads(ns);
      int fetched = 0;
      int max_steps = days * 400 + 1000;

      for (Map.Entry<Integer, BlockHeader> me : heads.entrySet())
      {
        BlockHeader h = me.getValue();
        int steps = 0;
        while ((h != null) && (steps < max_steps))
        {
          int shard = h.getShardId();
          TreeMap<Integer, Rec> m = work.get(shard);
          if (m == null)
          {
            m = new TreeMap<>();
            work.put(shard, m);
          }
          Rec existing = m.get(h.getBlockHeight());
          if ((existing != null) && existing.hash.equals(h.getSnowHash()))
          {
            break; // joined the part of the chain we already know
          }
          if (h.getTimestamp() < cutoff) break;
          m.put(h.getBlockHeight(), new Rec(h));
          fetched++;
          if (h.getBlockHeight() == 0) break;
          h = cache.getHeader(new ChainHash(h.getPrevBlockHash()));
          steps++;
        }
      }

      // drop records that are too old, and records above the current head of a shard (reorg)
      TreeMap<Integer, ImmutableMap<Integer, Rec>> out = new TreeMap<>();
      for (Map.Entry<Integer, TreeMap<Integer, Rec>> me : work.entrySet())
      {
        int shard = me.getKey();
        TreeMap<Integer, Rec> m = me.getValue();
        while ((m.size() > 0) && (m.firstEntry().getValue().ts < cutoff)) m.pollFirstEntry();
        BlockHeader head = heads.get(shard);
        if (head != null)
        {
          while ((m.size() > 0) && (m.lastKey() > head.getBlockHeight())) m.pollLastEntry();
        }
        if (m.size() > 0) out.put(shard, ImmutableMap.copyOf(m));
      }
      records = ImmutableMap.copyOf(out);
      generated = System.currentTimeMillis();
      if (!ready || (fetched > 50))
      {
        logger.info("HistoryStats updated: " + fetched + " new headers, " + recordCount() + " blocks over " + days + " days in " + (generated - now) + " ms");
      }
      ready = true;
    }
    catch (Throwable t)
    {
      logger.log(Level.WARNING, "HistoryStats pass failed: " + t, t);
    }
  }

  /** Bucket the records for a range ending now */
  public Series series(long range_ms, long bucket_ms)
  {
    long now = System.currentTimeMillis();
    int n = (int) Math.max(1, range_ms / bucket_ms);
    long last_start = (now / bucket_ms) * bucket_ms;
    long first_start = last_start - (n - 1) * bucket_ms;

    ArrayList<Long> starts = new ArrayList<>();
    for (int i = 0; i < n; i++) starts.add(first_start + i * bucket_ms);

    Bucket[] total = newBuckets(starts, bucket_ms, now);
    TreeMap<Integer, ImmutableList<Bucket>> per_shard = new TreeMap<>();
    for (Map.Entry<Integer, ImmutableMap<Integer, Rec>> me : records.entrySet())
    {
      Bucket[] arr = newBuckets(starts, bucket_ms, now);
      for (Rec r : me.getValue().values())
      {
        if (r.ts < first_start) continue;
        int idx = (int) ((r.ts - first_start) / bucket_ms);
        if ((idx < 0) || (idx >= n)) continue;
        double hashes = Math.pow(2.0, r.diff);
        for (Bucket b : new Bucket[]{arr[idx], total[idx]})
        {
          b.blocks++;
          b.txs += r.txs;
          b.hashes += hashes;
          b.diff_sum += r.diff;
          b.tx_data += r.tx_data;
        }
      }
      per_shard.put(me.getKey(), ImmutableList.copyOf(arr));
    }

    Series s = new Series();
    s.generated = generated;
    s.range_ms = range_ms;
    s.bucket_ms = bucket_ms;
    s.starts = ImmutableList.copyOf(starts);
    s.total = ImmutableList.copyOf(total);
    s.per_shard = ImmutableMap.copyOf(per_shard);
    return s;
  }

  private static Bucket[] newBuckets(List<Long> starts, long bucket_ms, long now)
  {
    Bucket[] arr = new Bucket[starts.size()];
    for (int i = 0; i < arr.length; i++)
    {
      long start = starts.get(i);
      long end = Math.min(start + bucket_ms, now);
      arr[i] = new Bucket(start, Math.max(1, end - start));
    }
    return arr;
  }
}
