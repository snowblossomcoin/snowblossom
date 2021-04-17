package snowblossom.lib.db;

import snowblossom.lib.ChainHash;
import snowblossom.lib.trie.HashedTrie;
import snowblossom.proto.Block;
import snowblossom.proto.BlockSummary;
import snowblossom.proto.GoldSet;
import snowblossom.proto.Transaction;
import snowblossom.proto.ImportedBlock;

import java.math.BigInteger;

public interface DBFace
{
  public ProtoDBMap<Block> getBlockMap();
  public ProtoDBMap<BlockSummary> getBlockSummaryMap();
  public ProtoDBMap<Transaction> getTransactionMap();
  public ProtoDBMap<GoldSet> getGoldSetMap();
  public ProtoDBMap<ImportedBlock> getImportedBlockMap();

  public ChainHash getBlockHashAtHeight(int shard, int height);
  public ChainHash getBlockHashAtHeight(int height);
  
  public void setBlockHashAtHeight(int shard, int height, ChainHash hash);

  public BigInteger getBestBlockAt(int shard, int height);
  public void setBestBlockAt(int shard, int height, BigInteger work_sum);

  public DBMap getSpecialMap();
  public DBMapMutationSet getSpecialMapSet();

  public DBMapMutationSet getChildBlockMapSet();

  public HashedTrie getChainIndexTrie();
  public HashedTrie getUtxoHashedTrie();

  public boolean getBlockTrust(ChainHash hash);
  public void setBlockTrust(ChainHash hash);

}
