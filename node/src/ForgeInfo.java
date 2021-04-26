package snowblossom.node;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import duckutil.LRUCache;
import duckutil.Pair;
import duckutil.PeriodicThread;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;
import org.junit.Assert;
import snowblossom.lib.*;
import snowblossom.lib.trie.HashUtils;
import snowblossom.proto.*;


/**
 * Class for accessing various information needed to build new blocks in a sharded setup.
 *
 * Some data will be from shards this node is tracking and some from external shards
 */
public class ForgeInfo
{
	public static final int CACHE_SIZE=5000;

  private LRUCache<ByteString, BlockSummary> block_summary_cache = new LRUCache<>(CACHE_SIZE);
	private LRUCache<ChainHash, BlockHeader> block_header_cache = new LRUCache<>(CACHE_SIZE);
  private LRUCache<ChainHash, Map<String, ChainHash> > block_inclusion_cache = new LRUCache<>(CACHE_SIZE);

  private SnowBlossomNode node;

  public ForgeInfo(SnowBlossomNode node)
  {
    this.node = node;

  }

  public BlockSummary getSummary(ByteString bytes)
  {

    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.getSummary"))
    {
      synchronized(block_summary_cache)
      {
        BlockSummary bs = block_summary_cache.get(bytes);
        if (bs != null) return bs;
      }
      BlockSummary bs = node.getDB().getBlockSummaryMap().get(bytes);
      if (bs != null)
      {
        synchronized(block_summary_cache)
        {
          block_summary_cache.put(bytes, bs);
        }                                                                                                               
      }

      return bs;
    }
  }

  public BlockSummary getSummary(ChainHash hash)
  {
    return getSummary(hash.getBytes());
  }

  public BlockHeader getHeader(ChainHash hash)
  {
    synchronized(block_header_cache)
    {
      BlockHeader h = block_header_cache.get(hash);
      if (h != null) return h;
    }

    BlockHeader h = null;
    BlockSummary summary = getSummary(hash);
    if (summary != null)
    {
      h = summary.getHeader();
    }
    if (h == null)
    {
      ImportedBlock ib = node.getShardUtxoImport().getImportBlock(hash);
      if (ib != null)
      {
        h = ib.getHeader();
      }
    }

    if (h!=null)
    {
      synchronized(block_header_cache)
      {
        block_header_cache.put(hash, h);
      }
    }

    return h;

  }

  public BlockHeader getShardHead(int shard_id)
  {
    if (node.getBlockIngestor(shard_id) != null)
    {
      BlockSummary bs = node.getBlockIngestor(shard_id).getHead();
      if (bs != null) return bs.getHeader();

      return null;
    }

    ImportedBlock ib = node.getShardUtxoImport().getHighestKnownForShard(shard_id);
    if (ib != null)
    {
      return ib.getHeader();
    }
    return null;

  }

  /**
   * Return the set of shards that appear to be active on the network.
   * So this will be all shards that have blocks we know about minus those
   * who have both children shards having blocks of much higher height.
   */
  public Map<Integer, BlockHeader> getNetworkActiveShards()
  {
    TreeMap<Integer, BlockHeader> network_active = new TreeMap<>();
    int max_height = 0;

    for(int i=0; i<= node.getParams().getMaxShardId(); i++)
    {
			// We might not have info on intermediate shards that we are not tracking
      {
        BlockHeader h = getShardHead(i);
        if (h != null)
        {
          network_active.put(i, h);
          max_height = Math.max( max_height, h.getBlockHeight() );
        }
      }
    }

    // Remove shards where there are both children
    // that have enough height to not worry about
    TreeSet<Integer> to_remove = new TreeSet<>();

    for(int shard_id : network_active.keySet())
    {
      int h = network_active.get(shard_id).getBlockHeight();
      int child_count=0;
      for(int c : ShardUtil.getShardChildIds(shard_id))
      {
        if (network_active.containsKey(c))
        {
          int hc = network_active.get(c).getBlockHeight();
          if (hc > h + node.getParams().getMaxShardSkewHeight() * 2) child_count++;
        }
      }
      if (child_count == 2) to_remove.add(shard_id);
      if (h + node.getParams().getMaxShardSkewHeight() + 2 < max_height) to_remove.add(shard_id);
    }
    for(int x : to_remove)
    {
      network_active.remove(x);
    }

    return network_active;

  }


  public Map<String, ChainHash> getInclusionMap(ChainHash hash)
  {
    synchronized(block_inclusion_cache)
    {
      if (block_inclusion_cache.containsKey(hash))
        return block_inclusion_cache.get(hash);
    }
    
    Map<String, ChainHash> map = getInclusionMapInternal(hash, node.getParams().getMaxShardSkewHeight()+2);

    if (map != null)
    {
      map = ImmutableMap.copyOf(map);
      synchronized(block_inclusion_cache)
      {
        block_inclusion_cache.put(hash, map);
      }
    }
    return map;
  }
 
  private Map<String, ChainHash> getInclusionMapInternal(ChainHash hash, int depth)
  {
    HashMap<String, ChainHash> map = new HashMap<>(16,0.5f);

    BlockHeader h = getHeader(hash); 
    if (h == null) return null;
    Validation.checkCollisionsNT(map, h.getShardId(), h.getBlockHeight(), hash);
    Validation.checkCollisionsNT(map, h.getShardImportMap());
    
    BlockSummary bs = getSummary(hash);
    if(bs != null)
    {
      Validation.checkCollisionsNT(map, bs.getShardHistoryMap());
      return map;
    }
    if (depth > 0)
    {
      map.putAll(getInclusionMapInternal( new ChainHash(h.getPrevBlockHash()), depth-1));
    }

    return map;

  }


  /**
   * Go down by depth and then return all blocks that are decendend from that one
   * and are on the same shard id.
   */
  public Set<ChainHash> getBlocksAround(ChainHash start, int depth, int shard_id)
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ForgeInfo.getBlocksAround"))
    {
      ChainHash tree_root = descend(start, depth);


      return climb(tree_root, shard_id);
    }
  }

  public Set<ChainHash> climb(ChainHash start, int shard_id)
  {
    HashSet<ChainHash> set = new HashSet<>();
    BlockHeader h = getHeader(start);

    if (h != null)
    if (h.getShardId() == shard_id)
    {
      set.add(new ChainHash(h.getSnowHash()));
    }


    for(ByteString next : node.getDB().getChildBlockMapSet().getSet(start.getBytes(), 2000))
    {
      set.addAll(climb(new ChainHash(next), shard_id));
    }

    return set;

  }

  public ChainHash descend(ChainHash start, int depth)
  {
    if (depth==0) return start;

		BlockHeader h = getHeader(start);
    if (h == null) return start; // can't descend any more, just use this
    if (h.getBlockHeight()==0) return start;
    return descend(new ChainHash(h.getPrevBlockHash()), depth-1);

  }



}
