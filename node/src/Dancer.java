package snowblossom.node;

import com.google.protobuf.ByteString;
import java.util.Map;
import java.util.logging.Logger;
import snowblossom.lib.ChainHash;
import snowblossom.lib.ShardUtil;
import snowblossom.proto.*;

/** See section in Snowblossom Book about "The Dance"
 * https://docs.google.com/document/d/17cljhZnAiQTL9yzhZ_INxKx131LUzNs2paP2kCCgljo/edit#heading=h.2w202zag3l1f
 *
 */
public class Dancer
{
  private static final Logger logger = Logger.getLogger("snowblossom.node");
  private final SnowBlossomNode node;


  public Dancer(SnowBlossomNode node)
  {
    this.node = node;
  }
  
  public static boolean isCoordinator(int shard)
  {
    return (ShardUtil.getInheritSet(shard).contains(0));
  }
  public boolean isCompliant(BlockHeader header)
  {   
    boolean comp = isCompliant(header, 3);
    
    if (!comp)
    {
      logger.warning("Block out of compliance: " + node.getForgeInfo().getHeaderString(header));
    }

    return comp;
  }

  /**
   * Returns true iff the header is compliant with the dance.
   * namely, if it is a coordinator, it includes only compliant other blocks.
   * if it is not a coordinator, it includes only blocks the coordinator has included.
   */
  public boolean isCompliant(BlockHeader header, int depth)
  {
    if (depth==0) return true;
    if (header==null) return true; //this isn't a super strict check

    int import_depth = node.getParams().getMaxShardSkewHeight()*2+1;

    int shard_id = header.getShardId();
    if (isCoordinator(shard_id))
    {
      // For each imported block, make sure it is compliant
      for(Map.Entry<Integer, BlockImportList> me : header.getShardImportMap().entrySet())
      {
        int imp_shard_id = me.getKey();
        BlockImportList bil = me.getValue();
        for(Map.Entry<Integer, ByteString> me_bil : bil.getHeightMap().entrySet())
        {
          int imp_height = me_bil.getKey();
          ChainHash imp_hash = new ChainHash(me_bil.getValue());

          BlockHeader imp_head = node.getForgeInfo().getHeader(imp_hash);

          if (!isCompliant(imp_head, depth-1)) return false;
        }
      }
    }
    else
    {
      Map<Integer, BlockHeader> my_heads = node.getForgeInfo().getImportedShardHeads(header, import_depth);
      BlockHeader coord = node.getForgeInfo().getHighestCoordinator(my_heads.values());

      if (!isCompliant(coord, depth-1)) return false;
      if (coord == null)
      {
        logger.warning("Unable to find a coordinator for block: " + node.getForgeInfo().getHeaderString(header));
        return true;
      }

      Map<Integer, BlockHeader> coord_heads = node.getForgeInfo().getImportedShardHeads(coord, import_depth);

      // For each imported block in this current block, make sure that is in the coord_heads
      for(Map.Entry<Integer, BlockImportList> me : header.getShardImportMap().entrySet())
      {
        int imp_shard_id = me.getKey();
        BlockImportList bil = me.getValue();
        for(Map.Entry<Integer, ByteString> me_bil : bil.getHeightMap().entrySet())
        {
          int imp_height = me_bil.getKey();
          ChainHash imp_hash = new ChainHash(me_bil.getValue());

          BlockHeader imp_head = node.getForgeInfo().getHeader(imp_hash);

          if (!isCompliant(imp_head, depth-1)) return false;

          BlockHeader coord_head = coord_heads.get(imp_shard_id);

          // Coordinator doesn't have shard
          if (coord_head == null) return false;

          // We expect the block we have for this shard to be the same
          // or a parent of the shard in the coord_head
          if (!node.getForgeInfo().isInChain( coord_head, imp_head ))
          {
            return false;
          }
        }
      }
    }

    return true; // WOOO 


  }

}
