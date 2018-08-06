package snowblossom.lib.db;

import snowblossom.lib.ChainHash;
import snowblossom.proto.Block;
import snowblossom.proto.BlockSummary;
import snowblossom.proto.Transaction;

public interface DBFace
{
  public ProtoDBMap<Block> getBlockMap();
  public ProtoDBMap<BlockSummary> getBlockSummaryMap();
  public ProtoDBMap<Transaction> getTransactionMap();
  public DBMapMutationSet getAddressHistoryMap();
  public DBMapMutationSet getTransactionBlockMap();

  public DBMap getUtxoNodeMap();

  public ChainHash getBlockHashAtHeight(int height);
  public void setBlockHashAtHeight(int height, ChainHash hash);

  public DBMap getSpecialMap();
  public DBMapMutationSet getSpecialMapSet();


}
