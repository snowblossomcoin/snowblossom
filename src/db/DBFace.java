package snowblossom.db;

import snowblossom.proto.BlockSummary;
import snowblossom.proto.Block;

public interface DBFace
{
  public ProtoDBMap<Block> getBlockMap();
  public ProtoDBMap<BlockSummary> getBlockSummaryMap();

}
