package snowblossom.node;

import snowblossom.lib.ShardUtil;
import snowblossom.proto.*;

/** See section in Snowblossom Book about "The Dance"
 * https://docs.google.com/document/d/17cljhZnAiQTL9yzhZ_INxKx131LUzNs2paP2kCCgljo/edit#heading=h.2w202zag3l1f
 *
 */
public class Dancer
{
  private final SnowBlossomNode node;

  public Dancer(SnowBlossomNode node)
  {
    this.node = node;
  }
  
  public static boolean isCoordinator(int shard)
  {
    return (ShardUtil.getInheritSet(shard).contains(0));
  }

  /**
   * Returns true iff the header is compliant with the dance.
   * namely, if it is a coordinator, it includes only compliant other blocks.
   * if it is not a coordinator, it includes only blocks the coordinator has included.
   */
  public boolean isCompliant(BlockHeader header)
  {
    int shard_id = header.getShardId();
    if (isCoordinator(shard_id))
    {


    }
    else
    {



    }

    return true; // WOOO 


  }

}
