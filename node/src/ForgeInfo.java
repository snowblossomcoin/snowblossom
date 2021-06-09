package snowblossom.node;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import duckutil.SoftLRUCache;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.proto.*;

/**
 * Class for accessing various information needed to build new blocks in a sharded setup.
 *
 * Some data will be from shards this node is tracking and some from external shards
 */
public class ForgeInfo
{
  private static final Logger logger = Logger.getLogger("snowblossom.node");
  public static final int CACHE_SIZE=16*6*3*100;

  private SoftLRUCache<ChainHash, BlockSummary> block_summary_cache = new SoftLRUCache<>(CACHE_SIZE);
  private SoftLRUCache<ChainHash, BlockHeader> block_header_cache = new SoftLRUCache<>(CACHE_SIZE);
  private SoftLRUCache<ChainHash, Map<String, ChainHash> > block_inclusion_cache = new SoftLRUCache<>(CACHE_SIZE);

  private Map<Integer, ChainHash> ext_coord_head = new TreeMap<>();

  private SnowBlossomNode node;

  public ForgeInfo(SnowBlossomNode node)
  {
    this.node = node;

  }

  public BlockSummary getSummary(ByteString bytes)
  {
    return getSummary(new ChainHash(bytes));
  }

  public BlockSummary getSummary(ChainHash hash)
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ForgeInfo.getSummary"))
    {
      synchronized(block_summary_cache)
      {
        BlockSummary bs = block_summary_cache.get(hash);
        if (bs != null) return bs;
      }
      BlockSummary bs;

      try(TimeRecordAuto tra_miss = TimeRecord.openAuto("ForgeInfo.getSummary_miss"))
      {
        bs = node.getDB().getBlockSummaryMap().get(hash.getBytes());
      }
      if (bs != null)
      {
        synchronized(block_summary_cache)
        {
          block_summary_cache.put(hash, bs);
        }
      }
      return bs;
    }
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
      h = node.getDB().getBlockHeaderMap().get(hash.getBytes());
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

  public void saveExtCoordHead(int shard_id, ChainHash hash)
  {
    logger.fine(String.format("Saving ext coord head: %d %s", shard_id, hash.toString()));
    synchronized(ext_coord_head)
    {
      ext_coord_head.put(shard_id, hash);
    }

  }

  public BlockHeader getShardHead(int shard_id)
  {
    if (node.getBlockIngestor(shard_id) != null)
    {
      BlockSummary bs = node.getBlockIngestor(shard_id).getHead();
      if (bs != null) return bs.getHeader();

      return null;
    }

    ChainHash ext_coord_head_hash = null;
    synchronized(ext_coord_head)
    {
      if (ext_coord_head.containsKey(shard_id))
      {
        ext_coord_head_hash = ext_coord_head.get(shard_id);
      }
    }
    if (ext_coord_head_hash != null)
    {
      return getHeader(ext_coord_head_hash);
    }

    Set<ChainHash> head_list = node.getShardUtxoImport().getHighestKnownForShard(shard_id);

    // send them all, not just one random
    ArrayList<ChainHash> lst = new ArrayList<>();
    lst.addAll(head_list);
    if (lst.size() == 0) return null;
    Collections.shuffle(lst);
    ImportedBlock ib = node.getShardUtxoImport().getImportBlock( lst.get(0) );
    if (ib != null) return ib.getHeader();

    return null;

  }

  public List<BlockHeader> getShardHeads(int shard_id)
  {
    List<BlockHeader> out = new LinkedList<>();

    if (node.getInterestShards().contains(shard_id))
    if (node.getBlockIngestor(shard_id) != null)
    {
      BlockSummary bs = node.getBlockIngestor(shard_id).getHead();
      if (bs != null) out.add(bs.getHeader());
      return out;
    }

    ChainHash ext_coord_head_hash = null;
    synchronized(ext_coord_head)
    {
      if (ext_coord_head.containsKey(shard_id))
      {
        ext_coord_head_hash = ext_coord_head.get(shard_id);
      }
    }
    if (ext_coord_head_hash != null)
    {
      BlockHeader bh = getHeader(ext_coord_head_hash);
      if (bh != null)
      {
        return ImmutableList.of(bh);
      }
      else
      {
        logger.warning(String.format("We heard ext_coord_head_hash of %s but don't have the header",
          ext_coord_head_hash.toString()));
      }
    }

    Set<ChainHash> head_list = node.getShardUtxoImport().getHighestKnownForShard(shard_id);
    logger.fine(String.format("Get shard heads %d - %s", shard_id, head_list.toString()));

    // send them all, not just one random
    for(ChainHash hash : head_list)
    {
      ImportedBlock ib = node.getShardUtxoImport().getImportBlock( hash );
      if (ib == null)
      {
        logger.info(String.format("Request for shard %d head: %s but we do not have an imported block for that.",
          shard_id,
          hash.toString()));
      }
      else
      {
        out.add(ib.getHeader());
      }

    }
    return out;

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
      Map<String, ChainHash> sub = getInclusionMapInternal( new ChainHash(h.getPrevBlockHash()), depth-1);

      if (sub != null)
      map.putAll(sub);
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
      return climb(tree_root, shard_id, depth*2);
    }
  }

  /**
   * @param shard_id stay in this shard id, or if -1 then all blocks
   */
  public Set<ChainHash> climb(ChainHash start, int shard_id, int max_steps)
  {
    HashSet<ChainHash> set = new HashSet<>();
    if (max_steps <= 0) return set;

    BlockHeader h = getHeader(start);

    if (h != null)
    {
      if ((shard_id < 0) || (h.getShardId() == shard_id))
      {
        set.add(new ChainHash(h.getSnowHash()));
      }
      if (shard_id >= 0)
      {
        // If we are into a higher shard, there is no way to get to a lower one
        if (h.getShardId() > shard_id) return set;

      }
    }

    for(ByteString next : node.getDB().getChildBlockMapSet().getSet(start.getBytes(), 2000))
    {
      set.addAll(climb(new ChainHash(next), shard_id, max_steps-1));
    }
    if (set.size() > 1000)
    {
      logger.warning("Climb set over 1000: " + set.size() + " from " + start.toString());
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

  public List<BlockHeader> getImportPath(BlockSummary start, BlockHeader target)
  {
    return getImportPath(start.getImportedShardsMap(), target);
  }

  /**
   * Return the ordered list of blocks that need to be added to get from what
   * is imported in start to get to the target block.
   *
   * If such a path is impossible returns null. This could be because we lack
   * information about intermediate blocks, or the BlockSummary is already down some other path.
   *
   * If the target is already in the block summary, return an empty list.
   */
  public List<BlockHeader> getImportPath(Map<Integer,BlockHeader> start, BlockHeader target)
  {
    if (target == null) return null;

    int shard_id = target.getShardId();
    if (start.containsKey(shard_id))
    { // We have something for this shard
      BlockHeader included = start.get(shard_id);

      if (target.getBlockHeight() < included.getBlockHeight())
      { // We already have a block that is past this one, impossible
        return null;
      }
      if (target.getBlockHeight() == included.getBlockHeight())
      {
        if (target.getSnowHash().equals(included.getSnowHash()))
        {
          // We are home
          return new LinkedList<BlockHeader>();
        }
        else
        {
          // Wrong block is included at this height - impossible
          return null;
        }
      }
      if(target.getBlockHeight() > included.getBlockHeight())
      {
          BlockHeader prev = getHeader(new ChainHash(target.getPrevBlockHash()));

          List<BlockHeader> sub_list = getImportPath(start, prev);
          if (sub_list == null) return null;

          // If we reached it, then add ourselves on and done
          sub_list.add(target);
          return sub_list;
      }

      throw new RuntimeException("unreachable");

    }
    else
    { // The summary does not have the shard in question, just keep going down
      BlockHeader prev = getHeader(new ChainHash(target.getPrevBlockHash()));

      List<BlockHeader> sub_list = getImportPath(start, prev);
      if (sub_list == null) return null;

      // If we reached it, then add ourselves on and done
      sub_list.add(target);
      return sub_list;
    }
  }

  public static String getHeaderString(BlockHeader h)
  {
    if (h==null) return "null";

    int import_count =0;
    if (h.getVersion() >= 2)
    {
      for( BlockImportList bil : h.getShardImportMap().values())
      {
        import_count+=bil.getHeightMap().size();
      }
    }

    String hash = "blank";
    if (h.getSnowHash().size() > 0) hash = new ChainHash(h.getSnowHash()).toString();
    return String.format("{s:%d h:%d imp:%d %s}", h.getShardId(), h.getBlockHeight(), import_count, hash);
  }

  public String getBlockTextSummary(BlockHeader h)
  {
    StringBuilder sb = new StringBuilder();
    sb.append(getHeaderString(h));
    sb.append("\n");
    sb.append("  prev: " );
    sb.append(getHeaderString( getHeader(new ChainHash(h.getPrevBlockHash())) ));
    sb.append("\n");
    for( BlockImportList bil : h.getShardImportMap().values())
    {
      for(ByteString bytes : bil.getHeightMap().values())
      {
        ChainHash imp_hash = new ChainHash(bytes);
        BlockHeader imp_head = getHeader(imp_hash);
        sb.append("   - ");
        if (imp_head == null)
        {
          sb.append("no header import - " + imp_head);
        }
        else
        {
          sb.append(getHeaderString(imp_head));
        }
        sb.append("\n");
      }
    }

    return sb.toString();
  }

  /**
   * Return the highest block of shard that has been included in the given header
   * or null if no such information can be found.
   */
  public BlockHeader getLatestShard(BlockHeader h, int shard_id)
  {
    if (h==null) return null;
    Set<Integer> parents = ShardUtil.getAllParents(shard_id);
    parents.add(shard_id);

    if (parents.contains(h.getShardId())) return h;

    for(int s : parents)
    {
      if (h.getShardImportMap().containsKey(s))
      {
        BlockImportList bil = h.getShardImportMap().get(s);
        TreeSet<Integer> heights = new TreeSet<>();
        heights.addAll(bil.getHeightMap().keySet());

        ChainHash hash = new ChainHash(bil.getHeightMap().get(heights.last()));

        return getHeader(hash);
      }
    }
    return getLatestShard(getHeader(new ChainHash(h.getPrevBlockHash())), shard_id);
  }

  /**
   * Return true iff check is part of the chain ending with h
   */
  public boolean isInChain(BlockHeader h, BlockHeader check)
  {
    if (check == null) return false;
    if (h == null) return false;
    if (h.getBlockHeight() < check.getBlockHeight()) return false;
    if (h.getBlockHeight() == check.getBlockHeight())
    {
      return h.getSnowHash().equals(check.getSnowHash());
    }

    return isInChain( getHeader(new ChainHash(h.getPrevBlockHash())), check);
  }

  public Map<Integer, BlockHeader> getImportedShardHeads(BlockHeader bh, int depth)
  {
    return getImportedShardHeads(new ChainHash(bh.getSnowHash()), depth);
  }

  /**
   * Looking back at most 'depth' blocks, return the highest blocks imported in each shard
   */
  public Map<Integer, BlockHeader> getImportedShardHeads(ChainHash start_hash, int depth)
  {

    BlockHeader start = getHeader(start_hash);
    TreeMap<Integer, BlockHeader> map = new TreeMap<>();

    if (depth == 0) return map;
    if (start == null)
    {
      logger.warning(String.format("Unable to find header for %s - Looking for %d more",
        start_hash.toString(), depth));
      //throw new ValidationException(String.format("Unable to find header for %s - Looking for %d more",
      //        start_hash.toString(), depth));

      return map;
    }

    if (start.getBlockHeight() > 0)
    {
      mergeHighest(map, getImportedShardHeads( new ChainHash(start.getPrevBlockHash()),depth-1));
    }

    mergeHighest(map, start);

    // Then add my import blocks, which must be newer than those in prev blocks
    for(Map.Entry<Integer, BlockImportList> me : start.getShardImportMap().entrySet())
    {
      for(Map.Entry<Integer, ByteString> bil : me.getValue().getHeightMap().entrySet())
      {
        ChainHash hash = new ChainHash(bil.getValue());
        BlockHeader h = getHeader(hash);
        if (h != null)
        {
          mergeHighest(map, h);

          // Recurse into the coordinator as well
          if (Dancer.isCoordinator(h.getShardId()))
          {
            mergeHighest(map, getImportedShardHeads(hash, depth-1));
          }
        }
        else
        {
          logger.warning(String.format("Unable to find header for %s - Looking for %d more",
            hash.toString(), depth));
        }

      }

    }

    return map;

  }

  protected static void mergeHighest(Map<Integer, BlockHeader> map, BlockHeader h)
  {
    mergeHighest(map, ImmutableMap.of(h.getShardId(), h));
  }
  protected static void mergeHighest(Map<Integer, BlockHeader> map, Map<Integer, BlockHeader> add)
  {
    for(Map.Entry<Integer, BlockHeader> me : add.entrySet())
    {
      int shard = me.getKey();
      BlockHeader add_h = me.getValue();

      if ((!map.containsKey(shard)) || (map.get(shard).getBlockHeight() < add_h.getBlockHeight()))
      {
        map.put(shard,add_h);
      }
    }

  }


  /**
   * Get highest coordinator among this list
   */
  public static BlockHeader getHighestCoordinator(Collection<BlockHeader> lst)
  {
    BlockHeader highest = null;

    for(BlockHeader h : lst)
    {
      if (Dancer.isCoordinator(h.getShardId()))
      {
        if ((highest == null) || (h.getBlockHeight() > highest.getBlockHeight()))
        {
          highest = h;
        }
      }
    }

    return highest;

  }
}
