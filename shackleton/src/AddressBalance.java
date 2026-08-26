package snowblossom.shackleton;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.shackleton.ChainIndex.ShardStats;

/**
 * Balance of an address.
 *
 * Walking the UTXO trie for an address with tens of thousands of unspent outputs takes
 * a minute and has to be repeated after every block.  Instead we start from the rich list
 * snapshot (an exact walk of every shard's UTXO set, taken at a known block) and apply the
 * transactions of all blocks mined since then, which the chain index keeps in memory.
 * The mempool is applied on top for pending amounts.  The trie walk is only used when no
 * usable snapshot exists.
 */
public class AddressBalance
{
  private static final Logger logger = Logger.getLogger("snowblossom.shackleton");

  public long confirmed;
  public long pending;
  public long spendable;
  public int utxos;
  /** shard -> {confirmed, pending, spendable, utxos} */
  public final TreeMap<Integer, long[]> per_shard = new TreeMap<>();
  public List<TxView> mempool_txs = new ArrayList<>();
  public String method = "none";
  public int snapshot_height = -1;
  public long snapshot_time = 0;
  public int delta_blocks = 0;
  public int delta_txs = 0;
  public String error = null;

  private long[] shard(int s)
  {
    long[] a = per_shard.get(s);
    if (a == null)
    {
      a = new long[4];
      per_shard.put(s, a);
    }
    return a;
  }

  public static AddressBalance compute(Shackleton sh, AddressSpecHash addr)
  {
    AddressBalance ab = new AddressBalance();
    try
    {
      if (!ab.fromSnapshot(sh, addr))
      {
        ab.fromWalk(sh, addr);
      }
    }
    catch (Throwable t)
    {
      logger.log(Level.WARNING, "balance computation failed for " + addr.toAddressString(sh.getParams()) + ": " + t, t);
      ab.error = t.toString();
    }
    try
    {
      ab.applyMempool(sh, addr);
    }
    catch (Throwable t)
    {
      logger.log(Level.FINE, "mempool lookup failed: " + t);
    }
    ab.spendable = ab.confirmed + ab.pending;
    for (long[] a : ab.per_shard.values()) a[2] = a[0] + a[1];
    return ab;
  }

  /** @return false if no usable snapshot is available */
  private boolean fromSnapshot(Shackleton sh, AddressSpecHash addr)
  {
    RichList.Result rl = sh.getRichList().getResult();
    if (rl == null) return false;
    ChainIndex index = sh.getIndex();
    ChainCache cache = sh.getCache();
    Map<Integer, ShardStats> stats = index.getShardStats();
    if (stats.size() == 0) return false;

    // Every active shard must be covered by the snapshot and the blocks since then must be in the index
    ArrayList<BlockInfo> blocks = new ArrayList<>();
    for (ShardStats ss : stats.values())
    {
      int s = ss.shard;
      Integer h = rl.shard_heights.get(s);
      if (h == null) return false;
      List<BlockInfo> recent = index.getRecent(s);
      if (recent.size() == 0) return false;
      if (recent.get(recent.size() - 1).height > h + 1) return false; // gap
      // the snapshot block must still be part of the chain
      String snap_hash = rl.shard_hashes.get(s);
      if (snap_hash != null)
      {
        String actual = null;
        for (BlockInfo bi : recent)
        {
          if (bi.height == h)
          {
            actual = bi.hash.toString();
            break;
          }
        }
        if (actual == null)
        {
          BlockHeader bh = cache.getHeaderAtHeight(s, h);
          if (bh != null) actual = HexUtil.getHexString(bh.getSnowHash());
        }
        if ((actual == null) || !actual.equals(snap_hash)) return false;
      }
      for (int i = recent.size() - 1; i >= 0; i--)
      {
        BlockInfo bi = recent.get(i);
        if (bi.height > h) blocks.add(bi);
      }
    }

    String addr_str = addr.toAddressString(sh.getParams());
    RichList.Entry e = rl.find(addr_str);
    if (e != null)
    {
      confirmed = e.balance;
      utxos = e.utxos;
      for (Map.Entry<Integer, Long> me : e.shard_balance.entrySet())
      {
        long[] a = shard(me.getKey());
        a[0] += me.getValue();
        a[3] += e.shard_utxos.getOrDefault(me.getKey(), 0);
      }
    }
    snapshot_height = rl.max_height;
    snapshot_time = rl.generated;
    method = "snapshot";

    ByteString addr_bytes = addr.getBytes();
    for (BlockInfo bi : blocks)
    {
      Block blk = cache.getBlock(bi.hash);
      if (blk == null) return false;
      delta_blocks++;
      for (Transaction tx : blk.getTransactionsList())
      {
        TransactionInner inner;
        try
        {
          inner = TransactionUtil.getInner(tx);
        }
        catch (Throwable t)
        {
          continue;
        }
        boolean involved = false;
        for (TransactionInput in : inner.getInputsList())
        {
          if (addr_bytes.equals(in.getSpecHash())) { involved = true; break; }
        }
        if (!involved)
        {
          for (TransactionOutput out : inner.getOutputsList())
          {
            if (addr_bytes.equals(out.getRecipientSpecHash())) { involved = true; break; }
          }
        }
        if (!involved) continue;
        delta_txs++;
        cache.cacheTransaction(tx);
        TxView tv = TxView.of(tx, cache, true);
        for (TxView.InputView iv : tv.inputs)
        {
          if (!iv.address.equals(addr)) continue;
          long v = Math.max(0, iv.value);
          confirmed -= v;
          utxos--;
          long[] a = shard(bi.shard);
          a[0] -= v;
          a[3]--;
        }
        for (TransactionOutput out : inner.getOutputsList())
        {
          if (!addr_bytes.equals(out.getRecipientSpecHash())) continue;
          confirmed += out.getValue();
          utxos++;
          int target = out.getTargetShard();
          if (!stats.containsKey(target)) target = bi.shard;
          long[] a = shard(target);
          a[0] += out.getValue();
          a[3]++;
        }
      }
    }
    return true;
  }

  /** Full UTXO trie walk through the client library (slow for large addresses) */
  private void fromWalk(Shackleton sh, AddressSpecHash addr)
    throws ValidationException
  {
    method = "walk";
    Map<String, TransactionBridge> bridges = sh.getUtxoUtil().getSpendableWithMempool(addr);
    for (TransactionBridge b : bridges.values())
    {
      if ((b.out == null) || (b.value < 0)) continue;
      if (b.unconfirmed) continue; // mempool is applied separately
      confirmed += b.value;
      utxos++;
      long[] a = shard(b.shard_id);
      a[0] += b.value;
      a[3]++;
    }
  }

  private void applyMempool(Shackleton sh, AddressSpecHash addr)
  {
    ChainCache cache = sh.getCache();
    TransactionShardMap tsm = sh.getStub().getMempoolTransactionMap(RequestAddress.newBuilder().setAddressSpecHash(addr.getBytes()).build());
    for (Map.Entry<Integer, TransactionHashList> me : new TreeMap<>(tsm.getShardMapMap()).entrySet())
    {
      int s = me.getKey();
      for (ByteString h : me.getValue().getTxHashesList())
      {
        Transaction tx = cache.getTransaction(new ChainHash(h));
        if (tx == null) continue;
        TxView tv = TxView.of(tx, cache, true);
        mempool_txs.add(tv);
        long net = tv.netForAddress(addr);
        pending += net;
        shard(s)[1] += net;
      }
    }
  }

  public String describe()
  {
    if (method.equals("snapshot"))
    {
      return "UTXO snapshot at height " + Html.num(snapshot_height) + " (" + Html.ago(snapshot_time) + ") plus " + delta_blocks + " newer block" + (delta_blocks == 1 ? "" : "s");
    }
    if (method.equals("walk")) return "from a full UTXO lookup";
    return "not available";
  }
}
