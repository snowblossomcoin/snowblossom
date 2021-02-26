package snowblossom.node;

import snowblossom.proto.*;
import snowblossom.lib.ShardUtil;
import snowblossom.lib.ChainHash;
import snowblossom.lib.ValidationException;
import snowblossom.lib.TransactionUtil;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.TreeMap;

import duckutil.LRUCache;
import com.google.protobuf.ByteString;

/**
 * Handles caching of ripping blocks apart to extract UTXO exports
 * and building ImportedBlock objects for block creation.
 *
 * Might end up using a decent chunk of ram, but only if get block template
 * is actually called.
 */
public class ShardUtxoImport
{

  private final SnowBlossomNode node;

  /*
   TOTO These objects might get big and we want to cache relative to number of
   active threads on the network.  Might want to get smarter here.
   */
  private LRUCache<ChainHash, ImportedBlock> cache = new LRUCache<>(300);

  public ShardUtxoImport(SnowBlossomNode node)
  {
    this.node = node;

  }

  public ImportedBlock getImportBlockForTarget(ChainHash hash, int target_shard)
  {
    ImportedBlock src = getImportBlock(hash);

    ImportedBlock.Builder ibb = ImportedBlock.newBuilder();
    ibb.setHeader(src.getHeader());

    // Copy the entries from our cover set into this object
    for(int s : ShardUtil.getCoverSet(target_shard, node.getParams()))
    {
      if (src.getImportOutputsMap().containsKey(s))
      {
        ibb.putImportOutputs(s, src.getImportOutputsMap().get(s));
      }

    }

    return ibb.build();

  }

  /**
   * Gets the ImportedBlock as if *all* the exported shards were to be imported.
   * That way this can be filtered for what is needed but cached as a whole thing.
   */
  private ImportedBlock getImportBlock(ChainHash hash)
  {
    synchronized(cache)
    {
      ImportedBlock ib = cache.get(hash);
      if (ib != null) return ib;
    }

    ImportedBlock.Builder ibb = ImportedBlock.newBuilder();

    Block blk = node.getDB().getBlockMap().get(hash.getBytes());

    ibb.setHeader(blk.getHeader());

    Set<Integer> cover_set = ShardUtil.getCoverSet(blk.getHeader().getShardId(), node.getParams());

    Map<Integer, ImportedOutputList.Builder> output_list_map = new TreeMap<>();


    for(Transaction tx : blk.getTransactionsList())
    {
      TransactionInner tx_inner = TransactionUtil.getInner(tx);

      ArrayList<ByteString> tx_out_wire_lst;
      try
      {
        tx_out_wire_lst = TransactionUtil.extractWireFormatTxOut(tx);
      }
      catch(ValidationException e)
      {
        throw new RuntimeException(e);
      } 

      int out_idx = 0;
      for(TransactionOutput tx_out : tx_inner.getOutputsList())
      {
        if (!cover_set.contains(tx_out.getTargetShard()))
        {
          int ts = tx_out.getTargetShard();
          if (!output_list_map.containsKey(ts))
          {
            output_list_map.put(ts, ImportedOutputList.newBuilder());
          }

          ImportedOutput io = ImportedOutput.newBuilder()
            .setRawOutput(tx_out_wire_lst.get(out_idx))
            .setTxId(tx.getTxHash())
            .setOutIdx(out_idx)
            .build();

          output_list_map.get(ts).addTxOuts(io);
        }
        

        out_idx++;
      }

    }

    for(Map.Entry<Integer, ImportedOutputList.Builder> me : output_list_map.entrySet())
    {
      ibb.putImportOutputs( me.getKey(), me.getValue().build() );
    }


    ImportedBlock ib = ibb.build();

    synchronized(cache)
    {
      cache.put(hash, ib);
    }

    return ib;

  }


}
