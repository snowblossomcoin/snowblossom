package snowblossom.node;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import duckutil.ExpiringLRUCache;
import duckutil.SoftLRUCache;
import duckutil.MetricLog;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.ChainHash;
import snowblossom.lib.ShardUtil;
import snowblossom.lib.TransactionUtil;
import snowblossom.lib.Validation;
import snowblossom.lib.ValidationException;
import snowblossom.lib.tls.MsgSigUtil;
import snowblossom.proto.*;

/**
 * Handles caching of ripping blocks apart to extract UTXO exports
 * and building ImportedBlock objects for block creation.
 *
 * Might end up using a decent chunk of ram, but only if get block template
 * is actually called.
 */
public class ShardUtxoImport
{
  private static final Logger logger = Logger.getLogger("snowblossom.node");

  private final SnowBlossomNode node;

  /*
   TOTO These objects might get big and we want to cache relative to number of
   active threads on the network.  Might want to get smarter here.
   */
  private SoftLRUCache<ChainHash, ImportedBlock> cache = new SoftLRUCache<>(16*16);

  private HashSet<AddressSpecHash> trusted_signers = new HashSet<>();

  private ExpiringLRUCache<ChainHash, Boolean> block_pull_cache = new ExpiringLRUCache<>(1000, 60000L);


  public static final int TRUST_MAX_DEPTH = 6;

  public ShardUtxoImport(SnowBlossomNode node)
    throws ValidationException
  {
    this.node = node;

    if (node.getConfig().isSet("trustnet_signers"))
    {
      for(String addr : node.getConfig().getList("trustnet_signers"))
      {
        AddressSpecHash spec = new AddressSpecHash(addr, "node");
        trusted_signers.add(spec);
      }
    }
  }

  public ImportedBlock getImportBlockForTarget(ChainHash hash, int target_shard)
  {
    try(TimeRecordAuto tra = TimeRecord.openAuto("ShardUtxoImport.getImportBlockForTarget"))
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

  }

  /**
   * Gets the ImportedBlock as if *all* the exported shards were to be imported.
   * That way this can be filtered for what is needed but cached as a whole thing.
   */
  public ImportedBlock getImportBlock(ChainHash hash)
  {
    synchronized(cache)
    {
      ImportedBlock ib = cache.get(hash);
      if (ib != null) return ib;
    }

    try(TimeRecordAuto tra = TimeRecord.openAuto("ShardUtxoImport.getImportBlock"))
    {

      ImportedBlock ib = null;

      if (node.getDB().getBlockTrust(hash))
      {
        ib = node.getDB().getImportedBlockMap().get(hash.getBytes());
      }

      if (ib == null)
      {
        ImportedBlock.Builder ibb = ImportedBlock.newBuilder();

        Block blk = node.getDB().getBlockMap().get(hash.getBytes());
        if (blk == null) return null;

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
        ib = ibb.build();
      }

      synchronized(cache)
      {
        cache.put(hash, ib);
      }
      return ib;
    }

  }


  /**
   * Take an inbound tip and see if it is signing a block
   * with a key we trust.  If so, trust that block and maybe some parent blocks.
   *
   * Returns a list of hashes to request ImportedBlock for
   */
  public List<ChainHash> checkTipTrust(MetricLog mlog, PeerChainTip tip)
    throws ValidationException
  {
    // If there is no block
    if (tip.getHeader().getSnowHash().size() == 0) return null;

    int shard_id = tip.getHeader().getShardId();
    
    // If this is a shard we actually track
    if (node.getInterestShards().contains(shard_id)) return null;

    mlog.set("trusted_signers", trusted_signers.size());

    // We trust no one
    if (trusted_signers.size() == 0) return null;
    
    if (tip.getSignedHead() == null) return null;
    if (tip.getSignedHead().getPayload().size() == 0) return null;

    SignedMessagePayload payload = MsgSigUtil.validateSignedMessage(tip.getSignedHead(), node.getParams());

    mlog.set("valid_payload", 1);

    AddressSpecHash signer = AddressUtil.getHashForSpec( payload.getClaim() );

    if (!trusted_signers.contains(signer)) return null;
    mlog.set("trusted_sig",1);

    
    // Now we have a valid signed head from a trusted signer
    logger.finer(String.format("Got signed tip from trusted peer (signer:%s)", 
      AddressUtil.getAddressString("node", signer)));
    
    PeerTipInfo tip_info = payload.getPeerTipInfo();

    LinkedList<ChainHash> request_list = new LinkedList<>();

    for(BlockPreview bp : tip_info.getPreviewsList())
    {
      ChainHash hash = new ChainHash(bp.getSnowHash());

      node.getDB().setBlockTrust(hash);
      node.getDB().getChildBlockMapSet().add( bp.getPrevBlockHash(), bp.getSnowHash());

      ImportedBlock ib = getImportBlock(hash);
      if (ib == null)
      {
        // If we don't have the block, maybe request it
        if (reserveBlock(hash))
        {
          request_list.add(hash);
        }
      }
    }

    return request_list;

  }

  public void addImportedBlock(ImportedBlock ib)
    throws ValidationException
  {
    Validation.validateImportedBlock(node.getParams(), ib);

    logger.info(String.format("Added ImportedBlock (s:%d h:%d %s)", 
      ib.getHeader().getShardId(),
      ib.getHeader().getBlockHeight(),
      new ChainHash(ib.getHeader().getSnowHash()).toString()));

    node.getDB().getChildBlockMapSet().add(ib.getHeader().getPrevBlockHash(), ib.getHeader().getSnowHash());
    node.getDB().getImportedBlockMap().put(ib.getHeader().getSnowHash(), ib);
    saveHighestForKnownShard( ib );
  }

  private boolean reserveBlock(ChainHash hash)
  {
    synchronized(block_pull_cache)
    {
      if (block_pull_cache.get(hash) != null) return false;

      block_pull_cache.put(hash, true);
      return true;
    }
  }

  public Set<ChainHash> getHighestKnownForShard(int shard)
  {
    ByteString key = ByteString.copyFrom(new String("ext-" + shard).getBytes());
    ExternalHeadList list = node.getDB().getExternalShardHeadMap().get(key); 

    HashSet<ChainHash> out = new HashSet<>();
    if (list != null)
    {
      for(ByteString bs : list.getHeadHashesList())
      {
        out.add(new ChainHash(bs));
      }
    }

    logger.info(String.format("Highest known for %d - %s", shard, out.toString()));
    
    return out;
  }

  private Object highest_known_lock = new Object();

  private void saveHighestForKnownShard(ImportedBlock ib)
  {
    int shard_id = ib.getHeader().getShardId();

    ByteString key = ByteString.copyFrom(new String("ext-" + shard_id).getBytes());
    ExternalHeadList.Builder list = ExternalHeadList.newBuilder();

    synchronized(highest_known_lock)
    {
      
      ExternalHeadList old = node.getDB().getExternalShardHeadMap().get(key);
      if (old != null) list.mergeFrom(old);

      if (list.getBlockHeight() > ib.getHeader().getBlockHeight()) return;

      if (list.getBlockHeight() < ib.getHeader().getBlockHeight())
      {
        list.clear();
        list.setBlockHeight(ib.getHeader().getBlockHeight());
      }
      else
      {
        for(ByteString bs : list.getHeadHashesList())
        {
          if (bs.equals(ib.getHeader().getSnowHash())) return;
        }
      }
      list.addHeadHashes( ib.getHeader().getSnowHash() );

      node.getDB().getExternalShardHeadMap().put(key, list.build());

    }

  }

}
