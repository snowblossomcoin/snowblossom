package snowblossom.node;

import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Logger;
import java.util.TreeMap;
import java.util.Set;
import java.util.HashSet;
import com.google.protobuf.ByteString;

import snowblossom.lib.*;
import snowblossom.proto.*;
import java.math.BigInteger;

public class MetaBlockForge
{

  private SnowBlossomNode node;
  private NetworkParams params;
  private static final Logger logger = Logger.getLogger("snowblossom.node");
  
  public MetaBlockForge(SnowBlossomNode node)
  {
    this.node = node;
    this.params = node.getParams();
  }

  // Moving to a new strategy
  // Switch getPath nonssense tht is reaching for head on other shards to
  // using the child map to reach for the highest work chain for each shard
  // if the head of that shard is in there, it will get used.
  // Then make block forge allow for passing in the prev_block to use
  // maybe try head, but then decend down the tree and then ascend back up
  // to get a bunch to try.  Take the highest work_sum.
  public Block getBlockTemplate(SubscribeBlockTemplateRequest mine_to)
  {
    Random rnd = new Random();
    ArrayList<Block> mineable = new ArrayList<>();
    TreeMap<Double, Block> mineable_map = new TreeMap<>();

    for(int shard_id : node.getActiveShards())
    {
        Block b = getBestBlockForShard(mine_to, shard_id);
        String block_str = "null";
        if (b != null)
        {
          block_str = String.format("s:%d h:%d imp:%d", 
            b.getHeader().getShardId(), b.getHeader().getBlockHeight(), b.getImportedBlocksCount());
        }
        logger.info(String.format("ZZZ Getting best block of shard %d: %s", shard_id, block_str));
        if (testBlock(b))
        {
          logger.info(String.format("ZZZ Minable best block of shard %d: %s", shard_id, block_str));
          mineable.add(b);
          mineable_map.put(rnd.nextDouble() + b.getHeader().getBlockHeight(), b);
        }
        logger.info(String.format("ZZZ failed best block of shard %d: %s", shard_id, block_str));
    }


    if (mineable.size() ==0)
    {
      logger.info(String.format("With active set %s, nothing minable", node.getActiveShards()));
      return null;
    }

    // TODO - lols
    //return mineable_map.lastEntry().getValue();
    return mineable.get( rnd.nextInt(mineable.size() ) );
  }

  private boolean testBlock(Block b)
  {
    if (b==null) return false;
    if (b.getHeader().getVersion() == 1) return true;
    if (b.getHeader().getBlockHeight()==0) return true;

    BlockSummary prev = node.getDB().getBlockSummaryMap().get(b.getHeader().getPrevBlockHash());
    try
    {
      BlockHeader bh = BlockHeader.newBuilder()
        .mergeFrom(b.getHeader())
        .setSnowHash(ChainHash.getRandom().getBytes())
        .build();
      Block test_block = Block.newBuilder().mergeFrom(b).setHeader(bh).build();

      Validation.checkShardBasics(test_block, prev, node.getParams());
      return true;
    }
    catch(ValidationException e)
    {
      logger.info("Validation failed: " + e);
      return false;
    }
  }

  /**
   * For the given shard, try to find the best next block we could make
   * usually this will mean extending from head, but not always.
   */
  private Block getBestBlockForShard(SubscribeBlockTemplateRequest mine_to, int shard_id)
  {
    if (shard_id == 0)
    {
      return node.getBlockForge(shard_id).getBlockTemplate(mine_to);
    }

    BlockSummary head = node.getBlockIngestor(shard_id).getHead();

    // This handles the case of starting a new shard
    if (head == null) return node.getBlockForge(shard_id).getBlockTemplate(mine_to);


    TreeMap<BigInteger, Block> possible_blocks = new TreeMap<>();

    Set<ChainHash> possible_source_blocks = getBlocks(new ChainHash(head.getHeader().getSnowHash()), 6, shard_id);
    System.out.println("ZZZ possible source blocks: " + possible_source_blocks.size());


    for(ChainHash start : possible_source_blocks)
    {
      BlockSummary prev = node.getDB().getBlockSummaryMap().get(start.getBytes());
      Block blk = node.getBlockForge(shard_id).getBlockTemplate(prev, mine_to);

      if (testBlock(blk))
      {
        BlockSummary new_summary = BlockchainUtil.getNewSummary(
          BlockHeader.newBuilder()
            .mergeFrom(blk.getHeader())
            .setSnowHash(ChainHash.getRandom().getBytes())
            .build(),
          prev, node.getParams(), 
          blk.getTransactionsCount(), 
          blk.getHeader().getTxDataSizeSum(), 
          blk.getImportedBlocksList());

        BigInteger work = BlockchainUtil.readInteger( new_summary.getWorkSum() );

        possible_blocks.put( work, blk);

      }

    }
    if (possible_blocks.size() == 0) return null;
    return possible_blocks.pollLastEntry().getValue();

  }

  /**
   * Go down by depth and then return all blocks that are decendend from that one
   * and are on the same shard id.
   */
  public Set<ChainHash> getBlocks(ChainHash start, int depth, int shard_id)
  {
    ChainHash tree_root = descend(start, shard_id);

    return climb(tree_root, shard_id);


  }

  public Set<ChainHash> climb(ChainHash start, int shard_id)
  {
    HashSet<ChainHash> set = new HashSet<>();
    BlockSummary bs = node.getDB().getBlockSummaryMap().get(start.getBytes());
  
    if (bs != null)
    if (bs.getHeader().getShardId() == shard_id)
    {
      set.add(new ChainHash(bs.getHeader().getSnowHash()));
    }

    for(ByteString next : node.getDB().getChildBlockMapSet().getSet(start.getBytes(), 20))
    {
      set.addAll(climb(new ChainHash(next), shard_id));
    }

    return set;
    
  }

  public ChainHash descend(ChainHash start, int depth)
  {
    if (depth==0) return start;

    BlockSummary bs = node.getDB().getBlockSummaryMap().get(start.getBytes());
    if (bs == null) return null;
    return descend(new ChainHash(bs.getHeader().getPrevBlockHash()), depth-1);

  }

  private void logState()
  {
    logger.info("My active shards: " + node.getActiveShards());
    for(int s : node.getActiveShards())
    {
      BlockSummary head = node.getBlockIngestor(s).getHead();
      if (head == null)
      {
        logger.info(String.format("  Shard %d - no head", s));
      }
      else
      {
        logger.info(String.format("  Shard %d - %s %d", s, 
          new ChainHash(head.getHeader().getSnowHash()).toString(), 
          head.getHeader().getBlockHeight()));
        for(int is : head.getImportedShardsMap().keySet())
        {
          BlockHeader h = head.getImportedShardsMap().get(is);
          logger.info(String.format("    Imported: %d - %s %d", is,
            new ChainHash(h.getSnowHash()).toString(),
            h.getBlockHeight()));
        }
      }

    }

  }

}
