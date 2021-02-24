package snowblossom.node;

import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Logger;
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
    ArrayList<Block> mineable = new ArrayList<>();

    for(int shard_id : node.getActiveShards())
    {
      try
      {
        Block b = node.getBlockForge(shard_id).getBlockTemplate(mine_to);
        if (b != null)
        mineable.add(b);
      }
      catch(Throwable e)
      {
        logger.info("Can't make block for shard: " + e);
        e.printStackTrace();
      }
    }

    Random rnd = new Random();
    if (mineable.size() ==0) return null;

    // TODO - lols
    return mineable.get( rnd.nextInt(mineable.size()));

  }

}
