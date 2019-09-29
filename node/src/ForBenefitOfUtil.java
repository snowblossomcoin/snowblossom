package snowblossom.node;
import com.google.common.collect.TreeMultimap;
import com.google.protobuf.ByteString;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import snowblossom.lib.*;
import snowblossom.lib.db.DB;
import snowblossom.lib.db.DBMapMutationSet;
import snowblossom.proto.*;
import snowblossom.trie.proto.TrieNode;

public class ForBenefitOfUtil
{
  public static final ByteString MAP_PREFIX = ByteString.copyFrom("fbo2".getBytes());

  public static void saveIndex(Block blk, Map<ByteString, ByteString> update_map)
  {
    int height = blk.getHeader().getBlockHeight();

    ChainHash blk_id = new ChainHash(blk.getHeader().getSnowHash());

    for(Transaction tx : blk.getTransactionsList())
    {
      TransactionInner inner = TransactionUtil.getInner(tx);

      int out_idx = 0;
      for(TransactionOutput out : inner.getOutputsList())
      {
        if (out.getForBenefitOfSpecHash().size() == Globals.ADDRESS_SPEC_HASH_LEN)
        {
          ByteString for_addr = out.getForBenefitOfSpecHash();
          ChainHash tx_id = new ChainHash(tx.getTxHash());

          ByteString key = getKey(for_addr, tx_id, out_idx);
          ByteString value = getValue(tx_id, out_idx);

          update_map.put( key, value);

        }
        // if has identifiers, save those as well.  Maybe as a hash, to avoid silly
        // buggers with naming
        out_idx++;
      }
    }
  }

  public static ByteString getKey(ByteString for_addr, ChainHash tx_id, int out_idx)
  {
    byte[] buff = new byte[Globals.ADDRESS_SPEC_HASH_LEN + Globals.BLOCKCHAIN_HASH_LEN + 4];

    ByteBuffer bb = ByteBuffer.wrap(buff);
    bb.put(for_addr.toByteArray());
    bb.put(tx_id.toByteArray());
    bb.putInt(out_idx);

    return MAP_PREFIX.concat(ByteString.copyFrom(buff));

  }


  public static ByteString getValue(ChainHash tx_id, int out_idx)
  {
    byte[] buff = new byte[Globals.BLOCKCHAIN_HASH_LEN + 4];

    ByteBuffer bb = ByteBuffer.wrap(buff);
    bb.put(tx_id.toByteArray());
    bb.putInt(out_idx);

    return ByteString.copyFrom(buff);
  }

  /*public static HistoryList getHistory(AddressSpecHash spec_hash, DB db, BlockSummary summary)
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
  }*/

}
