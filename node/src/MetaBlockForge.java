package snowblossom.node;

import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Logger;
import java.util.TreeMap;

import snowblossom.lib.*;
import snowblossom.proto.*;

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
        Block b = node.getBlockForge(shard_id).getBlockTemplate(mine_to);
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
