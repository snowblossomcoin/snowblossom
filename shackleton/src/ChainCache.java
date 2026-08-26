package snowblossom.shackleton;

import com.google.protobuf.ByteString;
import duckutil.SoftLRUCache;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;

/**
 * Caching layer in front of the node RPCs.
 * Everything keyed by hash is immutable on the chain, so it can be cached forever
 * (bounded only by memory).  Things that depend on the current head are cached briefly.
 */
public class ChainCache
{
  private static final Logger logger = Logger.getLogger("snowblossom.shackleton");

  private final Shackleton shackleton;

  private final SoftLRUCache<ChainHash, BlockHeader> header_cache = new SoftLRUCache<>(100000);
  private final SoftLRUCache<ChainHash, Block> block_cache = new SoftLRUCache<>(1000);
  private final SoftLRUCache<ChainHash, BlockSummary> summary_cache = new SoftLRUCache<>(20000);
  private final SoftLRUCache<ChainHash, Transaction> tx_cache = new SoftLRUCache<>(40000);
  private final SoftLRUCache<ChainHash, BlockInfo> info_cache = new SoftLRUCache<>(100000);
  private final SoftLRUCache<ChainHash, com.google.common.collect.ImmutableSet<ByteString>> block_addr_cache = new SoftLRUCache<>(20000);

  private volatile NodeStatus node_status;
  private volatile long node_status_time = 0;
  private final Object node_status_lock = new Object();

  public static final long NODE_STATUS_MAX_AGE = 2000L;

  public ChainCache(Shackleton shackleton)
  {
    this.shackleton = shackleton;
  }

  private UserServiceBlockingStub stub()
  {
    return shackleton.getStub();
  }

  // -------------------------------------------------------------
  // Node status (short lived cache)
  // -------------------------------------------------------------
  public NodeStatus getNodeStatus()
  {
    long now = System.currentTimeMillis();
    NodeStatus ns = node_status;
    if ((ns != null) && (now < node_status_time + NODE_STATUS_MAX_AGE)) return ns;

    synchronized (node_status_lock)
    {
      ns = node_status;
      now = System.currentTimeMillis();
      if ((ns != null) && (now < node_status_time + NODE_STATUS_MAX_AGE)) return ns;

      ns = stub().getNodeStatus(QueryUtil.nr());
      node_status = ns;
      node_status_time = System.currentTimeMillis();
      return ns;
    }
  }

  // -------------------------------------------------------------
  // Headers
  // -------------------------------------------------------------
  public BlockHeader getHeader(ChainHash hash)
  {
    if (hash == null) return null;
    synchronized (header_cache)
    {
      BlockHeader bh = header_cache.get(hash);
      if (bh != null) return bh;
    }
    BlockHeader bh = null;
    try
    {
      bh = stub().getBlockHeader(RequestBlockHeader.newBuilder().setBlockHash(hash.getBytes()).build());
    }
    catch (Throwable t)
    {
      logger.log(Level.FINE, "getHeader failed for " + hash + ": " + t);
      return null;
    }
    if ((bh == null) || (bh.getSnowHash().size() == 0)) return null;
    cacheHeader(bh);
    return bh;
  }

  public void cacheHeader(BlockHeader bh)
  {
    synchronized (header_cache)
    {
      header_cache.put(new ChainHash(bh.getSnowHash()), bh);
    }
  }

  /** Not cached: the mapping from height to hash can change in a reorg */
  public BlockHeader getHeaderAtHeight(int shard, int height)
  {
    if (height < 0) return null;
    try
    {
      BlockHeader bh = stub().getBlockHeader(
        RequestBlockHeader.newBuilder().setBlockHeight(height).setShardId(shard).build());
      if ((bh == null) || (bh.getSnowHash().size() == 0)) return null;
      cacheHeader(bh);
      return bh;
    }
    catch (Throwable t)
    {
      logger.log(Level.FINE, "getHeaderAtHeight failed for s" + shard + " h" + height + ": " + t);
      return null;
    }
  }

  // -------------------------------------------------------------
  // Blocks
  // -------------------------------------------------------------
  public Block getBlock(ChainHash hash)
  {
    if (hash == null) return null;
    synchronized (block_cache)
    {
      Block b = block_cache.get(hash);
      if (b != null) return b;
    }
    Block b = null;
    try
    {
      b = stub().getBlock(RequestBlock.newBuilder().setBlockHash(hash.getBytes()).build());
    }
    catch (Throwable t)
    {
      logger.log(Level.FINE, "getBlock failed for " + hash + ": " + t);
      return null;
    }
    if ((b == null) || (b.getHeader().getVersion() == 0)) return null;
    synchronized (block_cache)
    {
      block_cache.put(hash, b);
    }
    cacheHeader(b.getHeader());
    return b;
  }

  public Block getBlockAtHeight(int shard, int height)
  {
    BlockHeader bh = getHeaderAtHeight(shard, height);
    if (bh == null) return null;
    return getBlock(new ChainHash(bh.getSnowHash()));
  }

  // -------------------------------------------------------------
  // Block info
  // -------------------------------------------------------------
  public BlockInfo getBlockInfo(ChainHash hash)
  {
    if (hash == null) return null;
    synchronized (info_cache)
    {
      BlockInfo bi = info_cache.get(hash);
      if (bi != null) return bi;
    }
    Block b = getBlock(hash);
    if (b == null) return null;
    return getBlockInfo(b);
  }

  public BlockInfo getBlockInfo(Block b)
  {
    ChainHash hash = new ChainHash(b.getHeader().getSnowHash());
    synchronized (info_cache)
    {
      BlockInfo bi = info_cache.get(hash);
      if (bi != null) return bi;
    }
    BlockInfo bi = BlockInfo.fromBlock(b, shackleton.getParams());
    synchronized (info_cache)
    {
      info_cache.put(hash, bi);
    }
    return bi;
  }

  public BlockInfo getBlockInfoAtHeight(int shard, int height)
  {
    BlockHeader bh = getHeaderAtHeight(shard, height);
    if (bh == null) return null;
    return getBlockInfo(new ChainHash(bh.getSnowHash()));
  }

  /** All address spec hashes involved (as input or output) in the transactions of a block */
  public com.google.common.collect.ImmutableSet<ByteString> addressesInBlock(ChainHash hash)
  {
    synchronized (block_addr_cache)
    {
      com.google.common.collect.ImmutableSet<ByteString> s = block_addr_cache.get(hash);
      if (s != null) return s;
    }
    Block b = getBlock(hash);
    if (b == null) return null;
    java.util.HashSet<ByteString> set = new java.util.HashSet<>();
    for (Transaction tx : b.getTransactionsList())
    {
      try
      {
        TransactionInner inner = TransactionUtil.getInner(tx);
        for (TransactionInput in : inner.getInputsList()) set.add(in.getSpecHash());
        for (TransactionOutput out : inner.getOutputsList()) set.add(out.getRecipientSpecHash());
      }
      catch (Throwable t)
      {
      }
    }
    com.google.common.collect.ImmutableSet<ByteString> s = com.google.common.collect.ImmutableSet.copyOf(set);
    synchronized (block_addr_cache)
    {
      block_addr_cache.put(hash, s);
    }
    return s;
  }

  // -------------------------------------------------------------
  // Summaries
  // -------------------------------------------------------------
  public BlockSummary getSummary(ChainHash hash)
  {
    if (hash == null) return null;
    synchronized (summary_cache)
    {
      BlockSummary bs = summary_cache.get(hash);
      if (bs != null) return bs;
    }
    BlockSummary bs = null;
    try
    {
      bs = stub().getBlockSummary(RequestBlockSummary.newBuilder().setBlockHash(hash.getBytes()).build());
    }
    catch (Throwable t)
    {
      logger.log(Level.FINE, "getSummary failed for " + hash + ": " + t);
      return null;
    }
    if ((bs == null) || (bs.getHeader().getSnowHash().size() == 0)) return null;
    synchronized (summary_cache)
    {
      summary_cache.put(hash, bs);
    }
    cacheHeader(bs.getHeader());
    return bs;
  }

  // -------------------------------------------------------------
  // Transactions
  // -------------------------------------------------------------
  public Transaction getTransaction(ChainHash hash)
  {
    if (hash == null) return null;
    synchronized (tx_cache)
    {
      Transaction tx = tx_cache.get(hash);
      if (tx != null) return tx;
    }
    Transaction tx = null;
    try
    {
      tx = stub().getTransaction(RequestTransaction.newBuilder().setTxHash(hash.getBytes()).build());
    }
    catch (Throwable t)
    {
      logger.log(Level.FINE, "getTransaction failed for " + hash + ": " + t);
      return null;
    }
    if ((tx == null) || (tx.getInnerData().size() == 0)) return null;
    cacheTransaction(tx);
    return tx;
  }

  public void cacheTransaction(Transaction tx)
  {
    synchronized (tx_cache)
    {
      tx_cache.put(new ChainHash(tx.getTxHash()), tx);
    }
  }

  // -------------------------------------------------------------
  // Shard heads
  // -------------------------------------------------------------

  /** All shards the network is currently active on (as seen by the node), shard -> head header */
  public Map<Integer, BlockHeader> getNetShardHeads(NodeStatus ns)
  {
    TreeMap<Integer, BlockHeader> out = new TreeMap<>();
    for (Map.Entry<Integer, ByteString> me : ns.getNetShardHeadMap().entrySet())
    {
      BlockHeader bh = getHeader(new ChainHash(me.getValue()));
      if (bh != null) out.put(me.getKey(), bh);
    }
    if (out.size() == 0)
    {
      // Pre-shard node or nothing known yet, fall back to head summary
      if (ns.hasHeadSummary() && (ns.getHeadSummary().getHeader().getSnowHash().size() > 0))
      {
        BlockHeader bh = ns.getHeadSummary().getHeader();
        out.put(bh.getShardId(), bh);
      }
    }
    return out;
  }

  /** Shards the node is building blocks on (leaf shards it tracks), shard -> head header */
  public Map<Integer, BlockHeader> getBuildingShardHeads(NodeStatus ns)
  {
    TreeMap<Integer, BlockHeader> out = new TreeMap<>();
    for (Map.Entry<Integer, ByteString> me : ns.getShardHeadMap().entrySet())
    {
      BlockHeader bh = getHeader(new ChainHash(me.getValue()));
      if (bh != null) out.put(me.getKey(), bh);
    }
    if (out.size() == 0)
    {
      return getNetShardHeads(ns);
    }
    return out;
  }

  /** Current height of a shard, or -1 if unknown */
  public int getShardHeight(int shard)
  {
    NodeStatus ns = getNodeStatus();
    ByteString h = ns.getNetShardHeadMap().get(shard);
    if (h == null) h = ns.getShardHeadMap().get(shard);
    if (h == null) return -1;
    BlockHeader bh = getHeader(new ChainHash(h));
    if (bh == null) return -1;
    return bh.getBlockHeight();
  }

  public int cacheSizeHeaders() { synchronized (header_cache) { return header_cache.size(); } }
  public int cacheSizeBlocks() { synchronized (block_cache) { return block_cache.size(); } }
  public int cacheSizeTx() { synchronized (tx_cache) { return tx_cache.size(); } }
  public int cacheSizeInfo() { synchronized (info_cache) { return info_cache.size(); } }
}
