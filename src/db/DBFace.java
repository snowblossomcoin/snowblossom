package snowblossom.db;

import snowblossom.proto.BlockSummary;
import snowblossom.proto.Block;

import snowblossom.ChainHash;

public interface DBFace
{
  public ProtoDBMap<Block> getBlockMap();
  public ProtoDBMap<BlockSummary> getBlockSummaryMap();

  public DBMap getUtxoNodeMap();

  public ChainHash getBlockHashAtHeight(int height);
  public void setBlockHashAtHeight(int height, ChainHash hash);

  public DBMap getSpecialMap();


}
