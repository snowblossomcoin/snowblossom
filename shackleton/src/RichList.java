package snowblossom.shackleton;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import io.grpc.ManagedChannel;
import java.io.PrintStream;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.client.GetUTXOUtil;
import snowblossom.client.StubHolder;
import snowblossom.client.StubUtil;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc;

/**
 * Walks the full UTXO set of every shard and aggregates balances per address.
 */
public class RichList
{
  private static final Logger logger = Logger.getLogger("snowblossom.shackleton");

  public static void main(String args[])
    throws Exception
  {
    Globals.addCryptoProvider();
    if (args.length != 1)
    {
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: RichList <config_file>");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0], "snowblossom_");
    LogSetup.setup(config);

    RichList rl = new RichList(config);
    Result r = rl.compute();
    rl.print(r, System.out, Integer.MAX_VALUE);
  }

  private UserServiceBlockingStub stub;
  private NetworkParams params;
  private GetUTXOUtil get_utxo_util;

  private volatile Result last_result = null;

  public RichList(Config config)
    throws Exception
  {
    params = NetworkParams.loadFromConfig(config);
    ManagedChannel channel = StubUtil.openChannel(config, params);
    stub = UserServiceGrpc.newBlockingStub(channel);
    get_utxo_util = new GetUTXOUtil(new StubHolder(channel), params);
  }

  public RichList(NetworkParams params, UserServiceBlockingStub stub, GetUTXOUtil get_utxo_util)
  {
    this.params = params;
    this.stub = stub;
    this.get_utxo_util = get_utxo_util;
  }

  public static class Entry
  {
    public final String address;
    public final long balance;
    public final int utxos;
    public final ImmutableList<Integer> shards;
    public final ImmutableMap<Integer, Long> shard_balance;
    public final ImmutableMap<Integer, Integer> shard_utxos;

    Entry(String address, long balance, int utxos, ImmutableList<Integer> shards, Map<Integer, long[]> per_shard)
    {
      this.address = address;
      this.balance = balance;
      this.utxos = utxos;
      this.shards = shards;
      TreeMap<Integer, Long> sb = new TreeMap<>();
      TreeMap<Integer, Integer> su = new TreeMap<>();
      for (Map.Entry<Integer, long[]> me : per_shard.entrySet())
      {
        sb.put(me.getKey(), me.getValue()[0]);
        su.put(me.getKey(), (int) me.getValue()[1]);
      }
      this.shard_balance = ImmutableMap.copyOf(sb);
      this.shard_utxos = ImmutableMap.copyOf(su);
    }
  }

  public static class Result
  {
    public long generated;
    public long elapsed_ms;
    public int max_height;
    public long total;
    public int address_count;
    public long utxo_count;
    public ImmutableList<Entry> entries;
    public ImmutableMap<Integer, Long> shard_totals;
    public ImmutableMap<Integer, Long> shard_utxos;
    public ImmutableMap<Integer, Integer> shard_heights;
    public ImmutableMap<Integer, String> shard_hashes = ImmutableMap.of(); // hex hash of the head block the snapshot was taken at
    public long top10;
    public long top100;
    public long top1000;
    private ImmutableMap<String, Integer> index = ImmutableMap.of();

    void finish()
    {
      HashMap<String, Integer> idx = new HashMap<>(entries.size() * 2);
      for (int i = 0; i < entries.size(); i++)
      {
        long b = entries.get(i).balance;
        if (i < 10) top10 += b;
        if (i < 100) top100 += b;
        if (i < 1000) top1000 += b;
        idx.put(entries.get(i).address, i);
      }
      index = ImmutableMap.copyOf(idx);
      address_count = entries.size();
    }

    public Entry find(String address)
    {
      Integer i = index.get(address);
      if (i == null) return null;
      return entries.get(i);
    }

    public int rankOf(String address)
    {
      Integer i = index.get(address);
      if (i == null) return -1;
      return i + 1;
    }
  }

  private class Acc
  {
    long balance;
    int utxos;
    TreeSet<Integer> shards = new TreeSet<>();
    TreeMap<Integer, long[]> per_shard = new TreeMap<>(); // shard -> {balance, utxos}
  }

  /** Collects bridges from the UTXO walk without storing them */
  private class BridgeMapper extends AbstractCollection<TransactionBridge>
  {
    final HashMap<AddressSpecHash, Acc> map;
    final int shard;
    long total = 0L;
    long count = 0L;

    BridgeMapper(HashMap<AddressSpecHash, Acc> map, int shard)
    {
      this.map = map;
      this.shard = shard;
    }

    @Override
    public boolean add(TransactionBridge br)
    {
      AddressSpecHash spec = new AddressSpecHash(br.out.getRecipientSpecHash());
      Acc a = map.get(spec);
      if (a == null)
      {
        a = new Acc();
        map.put(spec, a);
      }
      a.balance += br.value;
      a.utxos++;
      a.shards.add(shard);
      long[] ps = a.per_shard.get(shard);
      if (ps == null)
      {
        ps = new long[2];
        a.per_shard.put(shard, ps);
      }
      ps[0] += br.value;
      ps[1]++;
      total += br.value;
      count++;
      return true;
    }

    @Override
    public int size() { return 0; }

    @Override
    public Iterator<TransactionBridge> iterator() { return Collections.emptyIterator(); }
  }

  public Result compute()
    throws Exception
  {
    long start = System.currentTimeMillis();
    NodeStatus node_status = stub.getNodeStatus(QueryUtil.nr());

    // Each leaf shard has its own UTXO set; together they make up the full set of coins
    TreeMap<Integer, ByteString> shard_heads = new TreeMap<>();
    shard_heads.putAll(node_status.getShardHeadMap());
    if (shard_heads.size() == 0)
    {
      BlockHeader h = node_status.getHeadSummary().getHeader();
      shard_heads.put(h.getShardId(), h.getSnowHash());
    }

    HashMap<AddressSpecHash, Acc> map = new HashMap<>(65536);
    TreeMap<Integer, Long> shard_totals = new TreeMap<>();
    TreeMap<Integer, Long> shard_utxos = new TreeMap<>();
    TreeMap<Integer, Integer> shard_heights = new TreeMap<>();
    TreeMap<Integer, String> shard_hashes = new TreeMap<>();
    long total = 0;
    long utxo_count = 0;
    int max_height = 0;

    for (Map.Entry<Integer, ByteString> me : shard_heads.entrySet())
    {
      int shard = me.getKey();
      BlockHeader head = stub.getBlockHeader(RequestBlockHeader.newBuilder().setBlockHash(me.getValue()).build());
      if ((head == null) || (head.getSnowHash().size() == 0)) continue;
      logger.info("Rich list: walking UTXO set of shard " + shard + " at height " + head.getBlockHeight());
      BridgeMapper bm = new BridgeMapper(map, shard);
      GetUTXOUtil.getSpendableValidatedStatic(bm, ByteString.EMPTY, stub, head.getUtxoRootHash(), shard);
      shard_totals.put(shard, bm.total);
      shard_utxos.put(shard, bm.count);
      shard_heights.put(shard, head.getBlockHeight());
      shard_hashes.put(shard, HexUtil.getHexString(head.getSnowHash()));
      total += bm.total;
      utxo_count += bm.count;
      max_height = Math.max(max_height, head.getBlockHeight());
    }

    ArrayList<Entry> entries = new ArrayList<>(map.size());
    for (Map.Entry<AddressSpecHash, Acc> me : map.entrySet())
    {
      Acc a = me.getValue();
      if (a.balance <= 0) continue;
      String addr = AddressUtil.getAddressString(params.getAddressPrefix(), me.getKey());
      entries.add(new Entry(addr, a.balance, a.utxos, ImmutableList.copyOf(a.shards), a.per_shard));
    }
    Collections.sort(entries, new Comparator<Entry>()
    {
      public int compare(Entry a, Entry b)
      {
        if (a.balance != b.balance) return Long.compare(b.balance, a.balance);
        return a.address.compareTo(b.address);
      }
    });

    Result r = new Result();
    r.generated = System.currentTimeMillis();
    r.elapsed_ms = r.generated - start;
    r.max_height = max_height;
    r.total = total;
    r.address_count = entries.size();
    r.utxo_count = utxo_count;
    r.entries = ImmutableList.copyOf(entries);
    r.shard_totals = ImmutableMap.copyOf(shard_totals);
    r.shard_utxos = ImmutableMap.copyOf(shard_utxos);
    r.shard_heights = ImmutableMap.copyOf(shard_heights);
    r.shard_hashes = ImmutableMap.copyOf(shard_hashes);
    r.finish();
    last_result = r;
    logger.info(String.format("Rich list: %d addresses, %d utxos, total %s in %d ms",
      r.address_count, r.utxo_count, Html.snow(r.total), r.elapsed_ms));
    return r;
  }

  public void print(Result r, PrintStream out, int max)
  {
    out.println("Highest block: " + r.max_height);
    out.println("Total value: " + Html.snow(r.total));
    out.println("Address count: " + r.address_count);
    int n = 0;
    for (Entry e : r.entries)
    {
      out.println(e.address + " " + Html.snow(e.balance));
      n++;
      if (n >= max) break;
    }
  }

  /** Save the result to a file so a restarted explorer has data right away */
  public void save(Result r, java.io.File f)
  {
    try
    {
      java.io.File tmp = new java.io.File(f.getPath() + ".tmp");
      try (PrintStream out = new PrintStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(tmp)), false, "UTF-8"))
      {
        out.println("shackleton-richlist-v2 " + params.getNetworkName() + " " + r.generated + " " + r.elapsed_ms + " " + r.max_height + " " + r.total + " " + r.utxo_count);
        for (Map.Entry<Integer, Long> me : r.shard_totals.entrySet())
        {
          out.println("shard " + me.getKey() + " " + me.getValue() + " " + r.shard_utxos.getOrDefault(me.getKey(), 0L) + " " + r.shard_heights.getOrDefault(me.getKey(), 0) + " " + r.shard_hashes.getOrDefault(me.getKey(), "-"));
        }
        for (Entry e : r.entries)
        {
          StringBuilder sb = new StringBuilder();
          sb.append(e.address).append(' ').append(e.balance).append(' ').append(e.utxos);
          for (int sh : e.shards)
          {
            sb.append(' ').append(sh).append('=').append(e.shard_balance.getOrDefault(sh, 0L)).append('=').append(e.shard_utxos.getOrDefault(sh, 0));
          }
          out.println(sb);
        }
      }
      if (!tmp.renameTo(f))
      {
        f.delete();
        tmp.renameTo(f);
      }
    }
    catch (Throwable t)
    {
      logger.log(Level.WARNING, "Unable to save rich list cache: " + t);
    }
  }

  /** Load a previously saved result, returns null if not available or not matching this network */
  public Result load(java.io.File f)
  {
    if (!f.exists()) return null;
    try (java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8")))
    {
      String head = in.readLine();
      if (head == null) return null;
      String[] h = head.split(" ");
      if ((h.length < 7) || !(h[0].equals("shackleton-richlist-v1") || h[0].equals("shackleton-richlist-v2")) || !h[1].equals(params.getNetworkName())) return null;
      Result r = new Result();
      r.generated = Long.parseLong(h[2]);
      r.elapsed_ms = Long.parseLong(h[3]);
      r.max_height = Integer.parseInt(h[4]);
      r.total = Long.parseLong(h[5]);
      r.utxo_count = Long.parseLong(h[6]);
      TreeMap<Integer, Long> shard_totals = new TreeMap<>();
      TreeMap<Integer, Long> shard_utxos = new TreeMap<>();
      TreeMap<Integer, Integer> shard_heights = new TreeMap<>();
      TreeMap<Integer, String> shard_hashes = new TreeMap<>();
      ArrayList<Entry> entries = new ArrayList<>();
      String line;
      while ((line = in.readLine()) != null)
      {
        String[] p = line.split(" ");
        if (p[0].equals("shard") && (p.length >= 5))
        {
          int shard = Integer.parseInt(p[1]);
          shard_totals.put(shard, Long.parseLong(p[2]));
          shard_utxos.put(shard, Long.parseLong(p[3]));
          shard_heights.put(shard, Integer.parseInt(p[4]));
          if ((p.length >= 6) && !p[5].equals("-")) shard_hashes.put(shard, p[5]);
          continue;
        }
        if (p.length < 3) continue;
        long balance = Long.parseLong(p[1]);
        int utxos = Integer.parseInt(p[2]);
        ArrayList<Integer> shards = new ArrayList<>();
        TreeMap<Integer, long[]> per_shard = new TreeMap<>();
        for (int i = 3; i < p.length; i++)
        {
          String[] q = p[i].split("=");
          int sh = Integer.parseInt(q[0]);
          shards.add(sh);
          if (q.length >= 3) per_shard.put(sh, new long[]{Long.parseLong(q[1]), Long.parseLong(q[2])});
        }
        if ((per_shard.size() == 0) && (shards.size() > 0))
        {
          // old format without per shard amounts: attribute everything to the first shard
          per_shard.put(shards.get(0), new long[]{balance, utxos});
        }
        entries.add(new Entry(p[0], balance, utxos, ImmutableList.copyOf(shards), per_shard));
      }
      r.entries = ImmutableList.copyOf(entries);
      r.address_count = entries.size();
      r.shard_totals = ImmutableMap.copyOf(shard_totals);
      r.shard_utxos = ImmutableMap.copyOf(shard_utxos);
      r.shard_heights = ImmutableMap.copyOf(shard_heights);
      r.shard_hashes = ImmutableMap.copyOf(shard_hashes);
      r.finish();
      last_result = r;
      logger.info("Loaded rich list cache from " + f + ": " + r.address_count + " addresses, computed " + Html.ago(r.generated));
      return r;
    }
    catch (Throwable t)
    {
      logger.log(Level.WARNING, "Unable to load rich list cache: " + t);
      return null;
    }
  }

  public Result getResult() { return last_result; }

  public long getTotalValue()
    throws Exception
  {
    Result r = last_result;
    if (r == null) throw new Exception("Not calculated yet");
    return r.total;
  }
}
