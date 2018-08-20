package snowblossom.node;

import snowblossom.lib.db.DB;
import snowblossom.proto.*;
import snowblossom.lib.Globals;
import snowblossom.lib.ChainHash;

import snowblossom.lib.trie.ByteStringComparator;
import com.google.common.collect.TreeMultimap;
import com.google.protobuf.ByteString;
import java.nio.ByteBuffer;

public class TransactionMapUtil
{
  public static void saveTransactionMap(Block blk, DB db)
  {
    TreeMultimap<ByteString, ByteString> tx_block_map = TreeMultimap.create(new ByteStringComparator(), new ByteStringComparator());
    ByteString value = getValue(blk);

    for(Transaction tx : blk.getTransactionsList())
    {
      tx_block_map.put(tx.getTxHash(),value);
    }

    db.getTransactionBlockMap().addAll(tx_block_map);
    
  }

  public static ByteString getValue(Block blk)
  {
    byte[] buff = new byte[4 + Globals.BLOCKCHAIN_HASH_LEN];

    ByteBuffer bb = ByteBuffer.wrap(buff);
    bb.putInt(blk.getHeader().getBlockHeight());
    bb.put(blk.getHeader().getSnowHash().toByteArray());

    return ByteString.copyFrom(buff);

  }

  public static TransactionStatus getTxStatus(ChainHash tx_id, DB db, BlockHeightCache cache, BlockSummary head_summary)
  {
    TransactionStatus.Builder status = TransactionStatus.newBuilder();

    status.setUnknown(true);
    for(ByteString val : db.getTransactionBlockMap().getSet(tx_id.getBytes(), 10000))
    {
      ByteBuffer bb = ByteBuffer.wrap(val.toByteArray());
      int height = bb.getInt();

      byte[] b = new byte[Globals.BLOCKCHAIN_HASH_LEN];

      bb.get(b);
      ChainHash blk_hash = new ChainHash(b);
			
			if (blk_hash.equals(cache.getHash(height)))
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
