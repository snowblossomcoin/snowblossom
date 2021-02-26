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
        if (b != null)
        {
          mineable.add(b);
          mineable_map.put(rnd.nextDouble() + b.getHeader().getBlockHeight(), b);
        }

      }
      catch(ValidationException e)
      {
      
        logger.info("Can't make block for shard: " + e);
        e.printStackTrace();
      }
      catch(Throwable e)
      {
        logger.info("Can't make block for shard: " + e);
        e.printStackTrace();
      }
    }

    if (mineable.size() ==0) return null;

    // TODO - lols
    return mineable_map.firstEntry().getValue();


  }

}
