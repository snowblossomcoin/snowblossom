package snowblossom.node;

import com.google.protobuf.ByteString;
import duckutil.LRUCache;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import snowblossom.lib.*;
import snowblossom.proto.*;

/**
 * Class for accessing various information needed to build new blocks in a sharded setup.
 *
 * Some data will be from shards this node is tracking and some from external shards
 */
public class ForgeInfo
{
  private LRUCache<ByteString, BlockSummary> block_summary_cache = new LRUCache<>(5000);

  private SnowBlossomNode node;

  public ForgeInfo(SnowBlossomNode node)
  {
    this.node = node;

  }

  public BlockSummary getDBSummary(ByteString bytes)                                                                      {

    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.getDBSummary"))
    {
      synchronized(block_summary_cache)
      {
        BlockSummary bs = block_summary_cache.get(bytes);
        if (bs != null) return bs;

      }
      BlockSummary bs = node.getDB().getBlockSummaryMap().get(bytes);                                                         if (bs != null)
      {
        synchronized(block_summary_cache)
        {
          block_summary_cache.put(bytes, bs);
        }                                                                                                               
      }

      return bs;
    }
  }

  public BlockSummary getDBSummary(ChainHash hash)
  {
    return getDBSummary(hash.getBytes());
  }


}
