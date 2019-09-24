package snowblossom.node;

import com.google.common.collect.TreeMultimap;
import com.google.protobuf.ByteString;
import java.nio.ByteBuffer;
import java.util.Map;
import snowblossom.lib.ChainHash;
import snowblossom.lib.Globals;
import snowblossom.lib.db.DB;
import snowblossom.lib.trie.ByteStringComparator;
import snowblossom.proto.*;

public class TransactionMapUtil
{

  public static final ByteString MAP_PREFIX = ByteString.copyFrom("tx2b".getBytes());

  // New style
  public static void saveTransactionMap(Block blk, Map<ByteString, ByteString> update_map)
  {
    ByteString value = getShortValue(blk);
    
    for(Transaction tx : blk.getTransactionsList())
    {
      update_map.put(MAP_PREFIX.concat(tx.getTxHash()),value);
    }
   
  }

  public static ByteString getShortValue(Block blk)
  {
    byte[] buff = new byte[4];

    ByteBuffer bb = ByteBuffer.wrap(buff);
    bb.putInt(blk.getHeader().getBlockHeight());

    return ByteString.copyFrom(buff);

  }

  public static TransactionStatus getTxStatus(ChainHash tx_id, DB db, BlockSummary head_summary)
  {
    TransactionStatus.Builder status = TransactionStatus.newBuilder();

    status.setUnknown(true);

    ByteString key = MAP_PREFIX.concat(tx_id.getBytes());
    ByteString val = db.getChainIndexTrie().getLeafData( head_summary.getChainIndexTrieHash(), key);
    if(val != null)
    {
      ByteBuffer bb = ByteBuffer.wrap(val.toByteArray());
      int height = bb.getInt();

			{
        status.setConfirmed(true);
        status.setHeightConfirmed(height);
        int curr_height = head_summary.getHeader().getBlockHeight();
        int depth = 1 + curr_height - height;
        status.setConfirmations(depth);
			}
    }
    return status.build();
    
  }
}
