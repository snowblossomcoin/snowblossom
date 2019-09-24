package snowblossom.node;
import com.google.common.collect.TreeMultimap;
import com.google.protobuf.ByteString;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import snowblossom.lib.*;
import snowblossom.lib.db.DB;
import snowblossom.lib.db.DBMapMutationSet;
import snowblossom.proto.*;
import snowblossom.trie.proto.TrieNode;


public class AddressHistoryUtil
{
  public static final ByteString MAP_PREFIX = ByteString.copyFrom("a2tx".getBytes());

  // NEW STYLE
  public static void saveAddressHistory(Block blk, Map<ByteString, ByteString> update_map)
  {
    TreeMultimap<ByteString, ByteString> map = DBMapMutationSet.createMap();
    int height = blk.getHeader().getBlockHeight();

    ChainHash blk_id = new ChainHash(blk.getHeader().getSnowHash());

    for(Transaction tx : blk.getTransactionsList())
    {
      ChainHash tx_id = new ChainHash(tx.getTxHash());
      ByteString val = getValue(blk_id, tx_id, height);
      TransactionInner inner = TransactionUtil.getInner(tx);

      for(TransactionInput in : inner.getInputsList())
      {
        ByteString addr = in.getSpecHash();
        update_map.put( MAP_PREFIX.concat(addr).concat(tx_id.getBytes()), val);
      }
      for(TransactionOutput out : inner.getOutputsList())
      {
        ByteString addr = out.getRecipientSpecHash();
        update_map.put( MAP_PREFIX.concat(addr).concat(tx_id.getBytes()), val);
      }
    }
  }


  public static ByteString getValue(ChainHash blk_id, ChainHash tx_id, int height)
  {
    byte[] buff = new byte[4 + Globals.BLOCKCHAIN_HASH_LEN * 2];

    ByteBuffer bb = ByteBuffer.wrap(buff);
    bb.putInt(height);
    bb.put(blk_id.toByteArray());
    bb.put(tx_id.toByteArray());

    return ByteString.copyFrom(buff);
  }

  public static HistoryList getHistory(AddressSpecHash spec_hash, DB db, BlockSummary summary)
    throws ValidationException
  {
    
    ByteString key = MAP_PREFIX.concat(spec_hash.getBytes());

    LinkedList<TrieNode> proof = new LinkedList<>();
    LinkedList<TrieNode> results = new LinkedList<>();
    db.getChainIndexTrie().getNodeDetails(
      summary.getChainIndexTrieHash(),
      key,
      proof,
      results,
      Globals.ADDRESS_HISTORY_MAX_REPLY);

    
    //List<ByteString> value_set = db.getAddressHistoryMap().getSet(spec_hash.getBytes(), Globals.ADDRESS_HISTORY_MAX_REPLY);

    HistoryList.Builder hist_list = HistoryList.newBuilder();

    for(TrieNode node : results)
    {
      if (node.getIsLeaf())
      {
        ByteString val = node.getLeafData();
        ByteBuffer bb = ByteBuffer.wrap(val.toByteArray());
        int height = bb.getInt();

        byte[] b = new byte[Globals.BLOCKCHAIN_HASH_LEN];

        bb.get(b);
        ChainHash blk_hash = new ChainHash(b);

        bb.get(b);
        ChainHash tx_hash = new ChainHash(b);

        //if (blk_hash.equals(cache.getHash(height)))
        {
          hist_list.addEntries(HistoryEntry
            .newBuilder()
            .setBlockHeight(height)
            .setTxHash(tx_hash.getBytes())
            .setBlockHash(blk_hash.getBytes())
            .build());
        }
      }
    }

    return hist_list.build();
  }

}
