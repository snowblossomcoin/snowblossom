package snowblossom.node;

import com.google.protobuf.ByteString;
import duckutil.LRUCache;
import duckutil.MetricLog;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.lib.db.DB;
import snowblossom.lib.trie.HashUtils;
import snowblossom.proto.Block;
import snowblossom.proto.BlockHeader;
import snowblossom.proto.BlockSummary;
import snowblossom.proto.ImportedBlock;
import snowblossom.proto.Transaction;

/**
 * This class takes in new blocks, validates them and stores them in the db.
 * In appropritate, updates the tip of the chain.
 */
public class BlockIngestor implements ChainStateSource
{
  private static final Logger logger = Logger.getLogger("snowblossom.blockchain");
  private SnowBlossomNode node;
  private DB db;
  private NetworkParams params;

  private volatile BlockSummary chainhead;

  public static final int SUMMARY_VERSION = 5;

  private LRUCache<ChainHash, Long> block_pull_map = new LRUCache<>(2000);
  private LRUCache<ChainHash, Long> tx_cluster_pull_map = new LRUCache<>(2000);

  private static PrintStream block_log;
  private static TimeRecord time_record;

  private final boolean tx_index;
  private final boolean addr_index;
  private final int shard_id;
  private final ByteString HEAD;


  public BlockIngestor(SnowBlossomNode node, int shard_id)
    throws Exception
  {
    this.node = node;
    this.db = node.getDB();
    this.params = node.getParams();
    this.shard_id = shard_id;
    if (shard_id == 0)
    {
      HEAD = ByteString.copyFrom(new String("head").getBytes());
    }
    else
    {
      HEAD = ByteString.copyFrom(new String("head-" + shard_id).getBytes());
    }

    tx_index = node.getConfig().getBoolean("tx_index");
    addr_index = node.getConfig().getBoolean("addr_index");

    {
    if (node.getConfig().isSet("block_log"))
    {
      if (block_log == null)
      {
      // TODO - there are multiple shards running BlockIngestor - this is all jacked
      block_log = new PrintStream(new FileOutputStream(node.getConfig().get("block_log"), true));
      time_record = new TimeRecord();
      TimeRecord.setSharedRecord(time_record);
      }
    }
    }

    chainhead = db.getBlockSummaryMap().get(HEAD);
    if (chainhead != null)
    {
      node.setStatus(String.format("Loaded chain tip: shard %d height %d %s",
        shard_id,
        chainhead.getHeader().getBlockHeight(),
        new ChainHash(chainhead.getHeader().getSnowHash())));
      checkResummary();

      // TODO remove after whatever is fixed
      updateHeights(chainhead, true);
    }


  }

  private void checkResummary()
  {
    BlockSummary curr = chainhead;
    LinkedList<ChainHash> recalc_list = new LinkedList<>();

    while(curr.getSummaryVersion() < SUMMARY_VERSION)
    {
      recalc_list.addFirst(new ChainHash(curr.getHeader().getSnowHash()));
      ChainHash prevblock = new ChainHash(curr.getHeader().getPrevBlockHash());

      if (prevblock.equals(ChainHash.ZERO_HASH))
      {
        curr = getStartSummary();
      }
      else
      {
        curr = db.getBlockSummaryMap().get( prevblock.getBytes() );
      }
    }

    if (recalc_list.size() > 0)
    {
      node.setStatus(String.format("Need to recalcuate chain index of %d blocks now", recalc_list.size()));

      for(ChainHash hash : recalc_list)
      {
        BlockSummary summary = db.getBlockSummaryMap().get( hash.getBytes() );
        Block blk = db.getBlockMap().get(hash.getBytes());
        node.setStatus("Reindexing: " + summary.getHeader().getBlockHeight() + " - " + hash + " - " + blk.getTransactionsCount());

        ChainHash prevblock = new ChainHash(summary.getHeader().getPrevBlockHash());
        BlockSummary prevsummary = null;
        if (prevblock.equals(ChainHash.ZERO_HASH))
        {
          prevsummary = getStartSummary();
        }
        else
        {
          prevsummary = db.getBlockSummaryMap().get( prevblock.getBytes() );
        }

        long tx_body_size = 0;
        for(Transaction tx : blk.getTransactionsList())
        {
          tx_body_size += tx.getInnerData().size();
          tx_body_size += tx.getTxHash().size();
        }

        summary = BlockchainUtil.getNewSummary(blk.getHeader(), prevsummary, node.getParams(), blk.getTransactionsCount(), tx_body_size, blk.getImportedBlocksList() );


        summary = saveOtherChainIndexBits(summary, prevsummary, blk);

        db.getBlockSummaryMap().put( hash.getBytes(), summary);

      }


      // Resave head
      chainhead = db.getBlockSummaryMap().get( chainhead.getHeader().getSnowHash());
      db.getBlockSummaryMap().put(HEAD, chainhead);


    }


  }

  private BlockSummary getStartSummary()
  {
    return BlockSummary.newBuilder()
      .setHeader(BlockHeader.newBuilder().setUtxoRootHash( HashUtils.hashOfEmpty() ).build())
      .setChainIndexTrieHash(  HashUtils.hashOfEmpty() )
      .setSummaryVersion(SUMMARY_VERSION)
      .build();
  }

  public boolean ingestBlock(Block blk)
    throws ValidationException
  {

    if (time_record != null) time_record.reset();

    ChainHash blockhash;
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("BlockIngestor.ingestBlock");
      MetricLog mlog = new MetricLog())
    {
      mlog.setOperation("ingest_block");
      mlog.setModule("block_ingestor");
      Validation.checkBlockBasics(node.getParams(), blk, true, false);

      if (blk.getHeader().getShardId() != shard_id)
      {
        throw new ValidationException("Block for incorrect shard");
      }

      blockhash = new ChainHash(blk.getHeader().getSnowHash());
      mlog.set("hash", blockhash.toString());
      mlog.set("height", blk.getHeader().getBlockHeight());
      mlog.set("shard", blk.getHeader().getShardId());
      mlog.set("size", blk.toByteString().size());
      mlog.set("tx_count", blk.getTransactionsCount());

      if (db.getBlockSummaryMap().containsKey(blockhash.getBytes() ))
      {
        return false;
      }

      ChainHash prevblock = new ChainHash(blk.getHeader().getPrevBlockHash());

      BlockSummary prev_summary;
      if (prevblock.equals(ChainHash.ZERO_HASH))
      {
        prev_summary = getStartSummary();
      }
      else
      {
        try(TimeRecordAuto tra_prv = TimeRecord.openAuto("BlockIngestor.getPrevSummary"))
        {
          prev_summary = db.getBlockSummaryMap().get( prevblock.getBytes() );
        }
      }

      if (prev_summary == null)
      {
        return false;
      }

      long tx_body_size = 0;
      for(Transaction tx : blk.getTransactionsList())
      {
        tx_body_size += tx.getInnerData().size();
        tx_body_size += tx.getTxHash().size();
      }

      BlockSummary summary = BlockchainUtil.getNewSummary(blk.getHeader(), prev_summary, node.getParams(), blk.getTransactionsCount(), tx_body_size, blk.getImportedBlocksList() );

      Validation.deepBlockValidation(node.getParams(), node.getUtxoHashedTrie(), blk, prev_summary);

      summary = saveOtherChainIndexBits(summary, prev_summary, blk);

      if (tx_index)
      {
        try(TimeRecordAuto tra_tx = TimeRecord.openAuto("BlockIngestor.saveTx"))
        {
          ByteString block_hash_str = blockhash.getBytes();
          HashMap<ByteString, Transaction> tx_map = new HashMap<>();
          for(Transaction tx : blk.getTransactionsList())
          {
            tx_map.put(tx.getTxHash(), tx);
          }
          db.getTransactionMap().putAll(tx_map);
        }
      }

      try(TimeRecordAuto tra_tx = TimeRecord.openAuto("BlockIngestor.blockSave"))
      {
        db.getBlockMap().put( blockhash.getBytes(), blk);


        saveBlockChildMapping( blk.getHeader().getPrevBlockHash(), blockhash.getBytes());
        for(ImportedBlock ib : blk.getImportedBlocksList())
        {
          // not positive we actually need this, but what the hell
          saveBlockChildMapping( ib.getHeader().getPrevBlockHash(), ib.getHeader().getSnowHash());
          node.getDB().getBlockHeaderMap().put( ib.getHeader().getSnowHash(), ib.getHeader());
        }
        db.setBestBlockAt( blk.getHeader().getShardId(), blk.getHeader().getBlockHeight(),
          BlockchainUtil.readInteger(summary.getWorkSum()));


        // THIS IS SUPER IMPORTANT!!!!
        // the summary being saved in the summary map acts as a signal that
        // - this block is fully stored
        //   - we have the utxo saved
        //   - we have the block itself saved
        //   - if we are using tx_index, we have the transactions saved
        // - the previous block summary is also saved, which by induction means
        //   that we have every block from this one all the way back to block 0
        // In short, after the summary is written, things can depend on this being
        // a valid and correct block that goes all the way back to block 0.
        // It might not be in the main chain, but it can be counted on to be valid chain
        db.getBlockSummaryMap().put( blockhash.getBytes(), summary);
        mlog.set("saved",1);
      }

      if (ShardUtil.shardSplit(summary, params))
      {
        for(int child : ShardUtil.getShardChildIds(summary.getHeader().getShardId()))
        {
          mlog.set("shard_split", 1);
          try
          {
            node.openShard(child);
          }
          catch(Exception e)
          {
            logger.warning("  Unable to open shard: " + e);
          }
        }
      }


      ChainHash prev_hash = new ChainHash(blk.getHeader().getPrevBlockHash());
      logger.info(String.format("New block: Shard %d Height %d %s (tx:%d sz:%d) - from %s", shard_id, blk.getHeader().getBlockHeight(), blockhash, blk.getTransactionsCount(), blk.toByteString().size(),prev_hash ));
      node.getBlockForge().tickle(summary);

      SnowUserService u = node.getUserService();
      if (u != null)
      {
        u.tickleBlocks();
      }


      if (BlockchainUtil.isBetter( chainhead, summary ))
      {
        mlog.set("head_update",1);
        chainhead = summary;
        db.getBlockSummaryMap().put(HEAD, summary);
        //System.out.println("UTXO at new root: " + HexUtil.getHexString(summary.getHeader().getUtxoRootHash()));
        //node.getUtxoHashedTrie().printTree(summary.getHeader().getUtxoRootHash());

        updateHeights(summary);

        logger.info(String.format("New chain tip: Shard %d Height %d %s (tx:%d sz:%d)", shard_id, blk.getHeader().getBlockHeight(), blockhash, blk.getTransactionsCount(), blk.toByteString().size()));

        String age = MiscUtils.getAgeSummary( System.currentTimeMillis() - blk.getHeader().getTimestamp() );

        logger.info(String.format("  The activated field is %d (%s).  This block was %s ago.",
          chainhead.getActivatedField(),
          params.getSnowFieldInfo(chainhead.getActivatedField()).getName(),
          age));

        if (u != null)
        {
          u.tickleBlocks();
        }
        node.getMemPool(shard_id).tickleBlocks(new ChainHash(summary.getHeader().getUtxoRootHash()));
        node.getPeerage().sendAllTips(summary.getHeader().getShardId());
      }

    }

    if (block_log != null)
    {
      block_log.println("-------------------------------------------------------------");
      block_log.println(String.format("Block{shard:%d height:%d - %s tx_count:%d size:%d }",
        blk.getHeader().getShardId(),
        blk.getHeader().getBlockHeight(),
        blockhash.toString(),
        blk.getTransactionsCount(),
        blk.toByteString().size()
      ));

      if (node.getConfig().getBoolean("block_log_full"))
      {
        for(Transaction tx : blk.getTransactionsList())
        {
          TransactionUtil.prettyDisplayTx(tx, block_log, params);
          block_log.println();
        }
      }
      time_record.printReport(block_log);
      time_record.reset();
    }

    return true;

  }


  /*
   * Update the chain index trie hash
   */
  private BlockSummary saveOtherChainIndexBits(BlockSummary summary_current, BlockSummary summary_prev, Block blk)
  {
    HashMap<ByteString, ByteString> update_map = new HashMap<>();

    logger.finer(String.format("indexes: %s %s", tx_index, addr_index));

    // Block to TX index
    if (tx_index)
    {
      TransactionMapUtil.saveTransactionMap(blk, update_map);
    }

    // Address to TX List
    if (addr_index)
    {
      AddressHistoryUtil.saveAddressHistory(blk, update_map);
    }

    // FBO Index
    ForBenefitOfUtil.saveIndex(blk, update_map);


    ChainHash prev_trie_hash = new ChainHash(summary_prev.getChainIndexTrieHash());


    ByteString new_hash_root = node.getDB().getChainIndexTrie().mergeBatch(
      summary_prev.getChainIndexTrieHash(), update_map);

    ChainHash new_trie_hash = new ChainHash(new_hash_root);

    logger.fine(String.format("Chain index hash %s -> %s - %d updates", prev_trie_hash, new_trie_hash, update_map.size()));

    return BlockSummary.newBuilder().mergeFrom(summary_current).setSummaryVersion(SUMMARY_VERSION).setChainIndexTrieHash(new_hash_root).build();
  }

  private void updateHeights(BlockSummary summary)
  {
    //Forcing a recheck because I think
    // there might be some race condition or something
    updateHeights(summary, true);
  }

  /**
   * This intentionally rewrites the heights of the blocks from the parent shards into this one
   * this way we allow for the positiblity of the parent shard blocks not being in the head chain of that
   * shard
   */
  private void updateHeights(BlockSummary summary, boolean force_recheck)
  {
    while(true)
    {
      int height = summary.getHeader().getBlockHeight();

      ChainHash found = db.getBlockHashAtHeight(shard_id, height);
      ChainHash hash = new ChainHash(summary.getHeader().getSnowHash());
      if ((found == null) || (!found.equals(hash)))
      {
        db.setBlockHashAtHeight(shard_id, height, hash);
      }
      else
      {
        if (!force_recheck)
        {
          return;
        }
      }
      if (height == 0) return;
      summary = db.getBlockSummaryMap().get(summary.getHeader().getPrevBlockHash());
    }
  }

  public BlockSummary getHead()
  {
    return chainhead;
  }

  @Override
  public int getHeight()
  {
    BlockSummary summ = getHead();
    if (summ == null) return 0;
    return summ.getHeader().getBlockHeight();
  }

  @Override
  public int getShardId()
  {
    return shard_id;
  }

  @Override
  public NetworkParams getParams()
  {
    return params;
  }

  @Override
  public Set<Integer> getShardCoverSet()
  {
    return ShardUtil.getCoverSet(shard_id, params);
  }

  public boolean reserveBlock(ChainHash hash)
  {
    synchronized(block_pull_map)
    {
      long tm = System.currentTimeMillis();
      if (block_pull_map.containsKey(hash) && (block_pull_map.get(hash) + 15000L > tm))
      {
        return false;
      }
      block_pull_map.put(hash, tm);
      return true;
    }
  }

  public boolean reserveTxCluster(ChainHash hash)
  {
    synchronized(tx_cluster_pull_map)
    {
      long tm = System.currentTimeMillis();
      if (tx_cluster_pull_map.containsKey(hash) && (tx_cluster_pull_map.get(hash) + 300000L > tm))
      {
        return false;
      }
      tx_cluster_pull_map.put(hash, tm);
      return true;
    }
  }


  private void saveBlockChildMapping(ByteString parent, ByteString child)
  {
    saveBlockChildMapping( new ChainHash(parent), new ChainHash(child) );
  }

  /**
   * Save the mapping of this parent to child block
   */
  private void saveBlockChildMapping(ChainHash parent, ChainHash child)
  {
    if (node.getDB().getChildBlockMapSet() != null)
    {
      node.getDB().getChildBlockMapSet().add(parent.getBytes(), child.getBytes());
    }
  }

}
