package snowblossom;

import com.google.protobuf.ByteString;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import snowblossom.db.DB;
import snowblossom.proto.Block;
import snowblossom.proto.BlockHeader;
import snowblossom.proto.BlockSummary;
import snowblossom.proto.Transaction;
import snowblossom.trie.HashUtils;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * This class takes in new blocks, validates them and stores them in the db.
 * In appropritate, updates the tip of the chain.
 */
public class BlockIngestor
{

  private static final Logger logger = Logger.getLogger("snowblossom.blockchain");
  private SnowBlossomNode node;
  private DB db;
  private NetworkParams params;
  
  private volatile BlockSummary chainhead;

  private static final ByteString HEAD = ByteString.copyFrom(new String("head").getBytes());

  private LRUCache<ChainHash, Long> block_pull_map = new LRUCache<>(1000);

  private PrintStream block_log;
  private TimeRecord time_record;

  private boolean tx_index=false;


  public BlockIngestor(SnowBlossomNode node)
    throws Exception
  {
    this.node = node;
    this.db = node.getDB();
    this.params = node.getParams();

    if (node.getConfig().isSet("block_log"))
    {
      block_log = new PrintStream(new FileOutputStream(node.getConfig().get("block_log"), true));
      time_record = new TimeRecord();
      TimeRecord.setSharedRecord(time_record);
    }

    chainhead = db.getBlockSummaryMap().get(HEAD);
    if (chainhead != null)
    {
      logger.info(String.format("Loaded chain tip: %d %s", 
        chainhead.getHeader().getBlockHeight(), 
        new ChainHash(chainhead.getHeader().getSnowHash())));
    }

    tx_index = node.getConfig().getBoolean("tx_index");

  }

  public boolean ingestBlock(Block blk)
    throws ValidationException
  {
    if (time_record != null) time_record.reset();

    ChainHash blockhash;
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("BlockIngestor.ingestBlock"))
    {
      Validation.checkBlockBasics(node.getParams(), blk, true);

      blockhash = new ChainHash(blk.getHeader().getSnowHash());

      if (db.getBlockSummaryMap().containsKey(blockhash.getBytes() ))
      {
        return false;
      }

      ChainHash prevblock = new ChainHash(blk.getHeader().getPrevBlockHash());

      BlockSummary prev_summary;
      if (prevblock.equals(ChainHash.ZERO_HASH))
      {
        prev_summary = BlockSummary.newBuilder()
            .setHeader(BlockHeader.newBuilder().setUtxoRootHash( HashUtils.hashOfEmpty() ).build())
          .build();
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

      BlockSummary summary = getNewSummary(blk.getHeader(), prev_summary, node.getParams(), blk.getTransactionsCount() );

      Validation.deepBlockValidation(node, blk, prev_summary);

      if (tx_index)
      {
        try(TimeRecordAuto tra_tx = TimeRecord.openAuto("BlockIngestor.saveTx"))
        {
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
      }

      BigInteger summary_work_sum = BlockchainUtil.readInteger(summary.getWorkSum());
      BigInteger chainhead_work_sum = BigInteger.ZERO;
      if (chainhead != null)
      {
        chainhead_work_sum = BlockchainUtil.readInteger(chainhead.getWorkSum());
      }

      if (summary_work_sum.compareTo(chainhead_work_sum) > 0)
      {
        chainhead = summary;
        db.getBlockSummaryMap().put(HEAD, summary);
        //System.out.println("UTXO at new root: " + HexUtil.getHexString(summary.getHeader().getUtxoRootHash()));
        //node.getUtxoHashedTrie().printTree(summary.getHeader().getUtxoRootHash());

        updateHeights(summary);

        logger.info(String.format("New chain tip: Height %d %s (tx:%d sz:%d)", blk.getHeader().getBlockHeight(), blockhash, blk.getTransactionsCount(), blk.toByteString().size()));

        SnowUserService u = node.getUserService();
        if (u != null)
        {
          u.tickleBlocks();
        }
        node.getMemPool().tickleBlocks(new ChainHash(summary.getHeader().getUtxoRootHash()));
      }


      node.getPeerage().sendAllTips();
    }

    if (block_log != null)
    {
      block_log.println("-------------------------------------------------------------");
      block_log.println("Block: " +  blk.getHeader().getBlockHeight() + " " + blockhash );
      for(Transaction tx : blk.getTransactionsList())
      {
        TransactionUtil.prettyDisplayTx(tx, block_log, params);
        block_log.println();
      }
      time_record.printReport(block_log);
      time_record.reset();


    }

    return true;

  }

  private void updateHeights(BlockSummary summary)
  {
    while(true)
    {
      int height = summary.getHeader().getBlockHeight();
      ChainHash found = db.getBlockHashAtHeight(height);
      ChainHash hash = new ChainHash(summary.getHeader().getSnowHash());
      if ((found == null) || (!found.equals(hash)))
      {
        db.setBlockHashAtHeight(height, hash);
        if (height == 0) return;
        summary = db.getBlockSummaryMap().get(summary.getHeader().getPrevBlockHash());
      }
      else
      {
        return;
      }
    }
    
  }

  public BlockSummary getHead()
  {
    return chainhead;
  }

  public int getHeight()
  {
    BlockSummary summ = getHead();
    if (summ == null) return 0;
    return summ.getHeader().getBlockHeight();
  }

  public static BlockSummary getNewSummary(BlockHeader header, BlockSummary prev_summary, NetworkParams params, long tx_count)
  {
    BlockSummary.Builder bs = BlockSummary.newBuilder();

    BigInteger target = BlockchainUtil.targetBytesToBigInteger(header.getTarget());

    BigInteger slice = BigInteger.valueOf(1024L);

    // So a block at max target is 'slice' number of work units
    // A block at half the target (harder) is twice the number of slices.
    BigInteger work_in_block = params.getMaxTarget().multiply(slice).divide(target);
    BigInteger prev_work_sum = BlockchainUtil.readInteger(prev_summary.getWorkSum());

    bs.setTotalTransactions( prev_summary.getTotalTransactions() + tx_count );

    BigInteger worksum = prev_work_sum.add(work_in_block);

    bs.setWorkSum(worksum.toString());

    long weight = params.getAvgWeight();
    long decay = 1000L - weight;
    BigInteger decay_bi = BigInteger.valueOf(decay);
    BigInteger weight_bi = BigInteger.valueOf(weight);

    long block_time;
    long prev_block_time;
    BigInteger prev_target_avg;

    if (prev_summary.getHeader().getTimestamp() == 0)
    { // first block, just pick a time
      block_time = params.getBlockTimeTarget();
      prev_block_time = params.getBlockTimeTarget();
      prev_target_avg = params.getMaxTarget();
    }
    else
    {
      block_time = header.getTimestamp() - prev_summary.getHeader().getTimestamp();
      prev_block_time = prev_summary.getBlocktimeAverageMs();
      prev_target_avg = BlockchainUtil.readInteger(prev_summary.getTargetAverage());
    }
    int field = prev_summary.getActivatedField();
    bs.setActivatedField( field );

    SnowFieldInfo next_field = params.getSnowFieldInfo(field + 1);
    if (next_field != null)
    {

      
      /*System.out.println(String.format("Field %d Target %f, activation %f", field+1,
        PowUtil.getDiffForTarget(prev_target_avg), 
        PowUtil.getDiffForTarget(next_field.getActivationTarget())));*/
      if (prev_target_avg.compareTo(next_field.getActivationTarget()) <= 0)
      {
        bs.setActivatedField( field + 1 );
      }
    }

    bs.setBlocktimeAverageMs(  (prev_block_time * decay + block_time * weight) / 1000L );

    bs.setTargetAverage( 
      prev_target_avg.multiply(decay_bi)
        .add(target.multiply(weight_bi))
        .divide(BigInteger.valueOf(1000L))
        .toString());

    bs.setHeader(header);
    
    return bs.build();

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

}
