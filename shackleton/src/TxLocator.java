package snowblossom.shackleton;

import duckutil.SoftLRUCache;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.proto.*;

/**
 * Finds the block (and therefore shard) a transaction is confirmed in.
 *
 * The node's GetTransactionStatus only consults the shard 0 chain index, which
 * is stale once the network has split into shards.  The address history index
 * however is maintained for every shard the node tracks, so we use that: look
 * up the history of an address involved in the transaction and pick the
 * entry for this transaction whose block is part of the current chain.
 */
public class TxLocator
{
  private static final Logger logger = Logger.getLogger("snowblossom.shackleton");

  public static class TxLocation
  {
    public final boolean confirmed;
    public final boolean mempool;
    public final int shard;
    public final int height;
    public final ChainHash block_hash;
    public final long timestamp;

    TxLocation(boolean confirmed, boolean mempool, int shard, int height, ChainHash block_hash, long timestamp)
    {
      this.confirmed = confirmed;
      this.mempool = mempool;
      this.shard = shard;
      this.height = height;
      this.block_hash = block_hash;
      this.timestamp = timestamp;
    }

    public static final TxLocation UNKNOWN = new TxLocation(false, false, -1, -1, null, 0);
    public static final TxLocation MEMPOOL = new TxLocation(false, true, -1, -1, null, 0);
  }

  private final Shackleton shackleton;
  private final SoftLRUCache<ChainHash, TxLocation> cache = new SoftLRUCache<>(50000);

  public TxLocator(Shackleton shackleton)
  {
    this.shackleton = shackleton;
  }

  public TxLocation locate(TxView tv)
  {
    synchronized (cache)
    {
      TxLocation loc = cache.get(tv.hash);
      if (loc != null) return loc;
    }
    TxLocation loc = locateInner(tv);
    if (loc.confirmed)
    {
      synchronized (cache)
      {
        cache.put(tv.hash, loc);
      }
    }
    return loc;
  }

  public int confirmations(TxLocation loc)
  {
    if (!loc.confirmed) return 0;
    int head = shackleton.getIndex().getHeightForConfirmations(loc.shard);
    if (head < 0)
    {
      head = shackleton.getCache().getShardHeight(loc.shard);
    }
    if (head < loc.height) return 1;
    return head - loc.height + 1;
  }

  private TxLocation locateInner(TxView tv)
  {
    ChainCache cc = shackleton.getCache();

    // Fast path 1: a coinbase names its block height (and shard)
    if (tv.coinbase)
    {
      CoinbaseExtras ex = tv.inner.getCoinbaseExtras();
      TxLocation loc = checkBlockAt(ex.getShardId(), ex.getBlockHeight(), tv.hash);
      if (loc != null) return loc;
      // the shard id is only set in version 2 blocks, try the shards that existed at that height
      for (int shard : candidateShards(ex.getBlockHeight()))
      {
        loc = checkBlockAt(shard, ex.getBlockHeight(), tv.hash);
        if (loc != null) return loc;
      }
    }

    // Fast path 2: the node's own index (based on the shard 0 chain) gives a height
    try
    {
      TransactionStatus status = shackleton.getStub().getTransactionStatus(
        RequestTransaction.newBuilder().setTxHash(tv.hash.getBytes()).build());
      if (status.getConfirmed())
      {
        int h = status.getHeightConfirmed();
        for (int shard : candidateShards(h))
        {
          TxLocation loc = checkBlockAt(shard, h, tv.hash);
          if (loc != null) return loc;
        }
      }
      else if (status.getMempool())
      {
        return TxLocation.MEMPOOL;
      }
    }
    catch (Throwable t)
    {
      logger.log(Level.FINE, "tx status lookup failed: " + t);
    }

    LinkedHashSet<AddressSpecHash> addrs = new LinkedHashSet<>(tv.involvedAddresses());
    boolean history_enabled = true;
    int tried = 0;
    List<HistoryEntry> candidates = new ArrayList<>();

    for (AddressSpecHash addr : addrs)
    {
      if (tried >= 4) break;
      tried++;
      HistoryList hl;
      try
      {
        hl = shackleton.getStub().getAddressHistory(RequestAddress.newBuilder().setAddressSpecHash(addr.getBytes()).build());
      }
      catch (Throwable t)
      {
        logger.log(Level.FINE, "history lookup failed: " + t);
        continue;
      }
      if (hl.getNotEnabled())
      {
        history_enabled = false;
        break;
      }
      for (HistoryEntry he : hl.getEntriesList())
      {
        if (tv.hash.equals(he.getTxHash()))
        {
          candidates.add(he);
        }
      }
      if (candidates.size() > 0) break;
    }

    if (candidates.size() > 0)
    {
      TxLocation best = null;
      TxLocation first = null;
      LinkedHashSet<ChainHash> seen_blocks = new LinkedHashSet<>();
      for (HistoryEntry he : candidates)
      {
        ChainHash bh = new ChainHash(he.getBlockHash());
        if (!seen_blocks.add(bh)) continue;
        BlockHeader header = cc.getHeader(bh);
        if (header == null) continue;
        TxLocation loc = new TxLocation(true, false, header.getShardId(), header.getBlockHeight(), bh, header.getTimestamp());
        if (first == null) first = loc;
        // is this block in the current chain of its shard?
        BlockHeader at = cc.getHeaderAtHeight(header.getShardId(), header.getBlockHeight());
        if ((at != null) && at.getSnowHash().equals(header.getSnowHash()))
        {
          best = loc;
          break;
        }
      }
      if (best != null) return best;
      if (first != null) return first;
    }

    // Fall back to the node's own status (works for non-sharded chains and mempool)
    try
    {
      TransactionStatus status = shackleton.getStub().getTransactionStatus(
        RequestTransaction.newBuilder().setTxHash(tv.hash.getBytes()).build());
      if (status.getMempool()) return TxLocation.MEMPOOL;
      if (status.getConfirmed() && !history_enabled)
      {
        int h = status.getHeightConfirmed();
        BlockHeader at = cc.getHeaderAtHeight(0, h);
        if (at != null)
        {
          return new TxLocation(true, false, at.getShardId(), h, new ChainHash(at.getSnowHash()), at.getTimestamp());
        }
        return new TxLocation(true, false, 0, h, null, 0);
      }
      if (status.getConfirmed() && (candidates.size() == 0))
      {
        // history index did not have it (e.g. address with more than the maximum history entries)
        int h = status.getHeightConfirmed();
        BlockHeader at = cc.getHeaderAtHeight(0, h);
        if (at != null)
        {
          return new TxLocation(true, false, at.getShardId(), h, new ChainHash(at.getSnowHash()), at.getTimestamp());
        }
      }
    }
    catch (Throwable t)
    {
      logger.log(Level.FINE, "tx status lookup failed: " + t);
    }
    return TxLocation.UNKNOWN;
  }

  /** Shards that could contain a block at the given height: active ones plus split ones covering it */
  private List<Integer> candidateShards(int height)
  {
    ArrayList<Integer> lst = new ArrayList<>();
    ChainIndex.ShardTree tree = shackleton.getIndex().getShardTree();
    if (tree != null)
    {
      for (ChainIndex.ShardNode n : tree.nodes.values())
      {
        if (n.active && (n.first_height < 0 || n.first_height <= height)) lst.add(n.id);
      }
      for (ChainIndex.ShardNode n : tree.nodes.values())
      {
        if (!n.active && (n.first_height < 0 || n.first_height <= height) && (n.last_height < 0 || n.last_height >= height)) lst.add(n.id);
      }
    }
    if (lst.size() == 0) lst.add(0);
    return lst;
  }

  /** If the block at (shard, height) in the current chain contains the tx, return its location */
  private TxLocation checkBlockAt(int shard, int height, ChainHash tx_hash)
  {
    if ((shard < 0) || (height < 0)) return null;
    ChainCache cc = shackleton.getCache();
    Block blk = cc.getBlockAtHeight(shard, height);
    if (blk == null) return null;
    for (Transaction tx : blk.getTransactionsList())
    {
      if (tx_hash.equals(tx.getTxHash()))
      {
        BlockHeader h = blk.getHeader();
        return new TxLocation(true, false, h.getShardId(), h.getBlockHeight(), new ChainHash(h.getSnowHash()), h.getTimestamp());
      }
    }
    return null;
  }
}
