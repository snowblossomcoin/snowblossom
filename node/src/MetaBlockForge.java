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
      try
      {
        Block b = getBestBlockForShard(mine_to, shard_id);
        if (b!=null)
        {
          if (b.getHeader().getVersion() == 2)
          {
            
            BlockSummary prev = node.getDB().getBlockSummaryMap().get(b.getHeader().getPrevBlockHash());
             
            BlockHeader bh = BlockHeader.newBuilder()
              .mergeFrom(b.getHeader())
              .setSnowHash(ChainHash.getRandom().getBytes())
              .build();
            Block test_block = Block.newBuilder().mergeFrom(b).setHeader(bh).build();

            Validation.checkShardBasics(test_block, prev, node.getParams());
          }

          mineable.add(b);
          mineable_map.put(rnd.nextDouble() + b.getHeader().getBlockHeight(), b);
        }

      }
      catch(ValidationException e)
      {
      
        logState();
        logger.info(String.format("Can't make block for shard %d: %s",shard_id, e));
        e.printStackTrace();
      }
      catch(Throwable e)
      {
        logger.info(String.format("Can't make block for shard %d: %s",shard_id, e));
        e.printStackTrace();
      }
    }


    if (mineable.size() ==0)
    {
      logger.info(String.format("With active set %s, nothing minable", node.getActiveShards()));
      return null;
    }

    // TODO - lols
    return mineable_map.firstEntry().getValue();
  }

  private boolean testBlock(BlockSummary prev, Block b)
  {
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

    for(ChainHash start : getBlocks(new ChainHash(head.getHeader().getSnowHash()), 6, shard_id))
    {
      BlockSummary bs = node.getDB().getBlockSummaryMap().get(start.getBytes());

      Block blk = node.getBlockForge(shard_id).getBlockTemplate(bs, mine_to);
      if (testBlock(bs, blk))
      {
        BlockSummary new_summary = BlockchainUtil.getNewSummary(
          BlockHeader.newBuilder()
            .mergeFrom(blk.getHeader())
            .setSnowHash(ChainHash.getRandom().getBytes())
            .build(),
          bs, node.getParams(), 
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

    return climb(start, shard_id);


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
    return descend(new ChainHash(bs.getHeader().getSnowHash()), depth-1);

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
