package snowblossom.node;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.proto.*;

public class FeeEstimator
{
  private static final Logger logger = Logger.getLogger("snowblossom.userservice");

  public static final long FEE_RECALC_TIME = 30000L;

  private SnowBlossomNode node;
  private final int shard_id;

  public FeeEstimator(SnowBlossomNode node, int shard_id)
  {
    this.node = node;
    this.shard_id = shard_id;
  }

  private long last_calc_time = 0L;
  private double last_fee = Globals.BASIC_FEE;


  public synchronized double getFeeEstimate()
  {
    if (last_calc_time + FEE_RECALC_TIME < System.currentTimeMillis())
    {
      recalcFee();
      logger.info("New fee estimate: " + last_fee);
    }
    return last_fee;
  }

  private void recalcFee()
  {
    BlockSummary head = node.getBlockIngestor(shard_id).getHead();
    ChainHash utxo_root = new ChainHash(head.getHeader().getUtxoRootHash());

    long test_size =  node.getParams().getMaxBlockSize()*2/3;

    List<Transaction> tx_list = node.getMemPool(shard_id).getTransactionsForBlock(utxo_root, (int)test_size);
    long total_size = 0L;
    long total_fee = 0L;

    for(Transaction tx : tx_list)
    {
      total_size += tx.toByteString().size();
      total_fee += TransactionUtil.getInner(tx).getFee();
    }

    logger.fine("Fee test size: " + total_size + " " + test_size);


    // Clearly not even full
    if (total_size < test_size - 50000)
    {
      last_fee = Globals.BASIC_FEE;
      last_calc_time = System.currentTimeMillis();
      return;
    }

    ArrayList<Transaction> tx_list_arr = new ArrayList<>();
    tx_list_arr.addAll(tx_list);

    long end_size = 0L;
    long end_fee = 0L; 

    for(int i = tx_list_arr.size() / 2; i<tx_list_arr.size(); i++)
    {
      Transaction tx = tx_list_arr.get(i);
      end_size += tx.toByteString().size();
      end_fee += TransactionUtil.getInner(tx).getFee();
    }
    double average_fee = (double) end_fee / (double) end_size;

    logger.fine("Average fee: " + average_fee + " " + end_fee + " " + end_size);

    last_fee = Math.max(Globals.BASIC_FEE, average_fee * 1.01);

    last_calc_time = System.currentTimeMillis();
  }

  


}
