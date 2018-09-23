package snowblossom.lib.db;

import com.google.protobuf.ByteString;
import duckutil.Config;
import snowblossom.lib.ChainHash;
import snowblossom.lib.DaemonThreadFactory;
import snowblossom.proto.Block;
import snowblossom.proto.BlockSummary;
import snowblossom.proto.Transaction;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DB implements DBFace
{
  private static final Logger logger = Logger.getLogger("snowblossom.db");
  protected int max_set_return_count=100000;

  protected Executor exec;
  protected ProtoDBMap<Block> block_map; 
  protected ProtoDBMap<BlockSummary> block_summary_map; 
  protected DBMap utxo_node_map;
  protected DBMap block_height_map;
  protected DBMap special_map;
  protected ProtoDBMap<Transaction> tx_map;
  protected DBMapMutationSet address_history_map;
  protected DBMapMutationSet special_map_set;
  protected DBMapMutationSet transaction_block_map;

  private Config config;
  private DBProvider prov;

  public DB(Config config, DBProvider prov)
    throws Exception
  {
    Runtime.getRuntime().addShutdownHook(new DBShutdownThread());
    this.config = config;
    this.prov = prov;
    open();

  }


  public void close()
  {
    prov.close();
  }

  public void open()
    throws Exception
  {
    block_map = new ProtoDBMap(Block.newBuilder().build().getParserForType(), prov.openMap("block"));
    tx_map = new ProtoDBMap(Transaction.newBuilder().build().getParserForType(), prov.openMap("tx"));
    block_summary_map = new ProtoDBMap(BlockSummary.newBuilder().build().getParserForType(), prov.openMap("blocksummary"));

    utxo_node_map = prov.openMap("u");
    block_height_map = prov.openMap("height");
    special_map = prov.openMap("special");

    try
    {
      // Lobstack for example doesn't have multation map set implemented
      // but most htings don't need this so whatever
      special_map_set = prov.openMutationMapSet("special_map_set");
    }
    catch(Throwable t){}

    if (config.getBoolean("addr_index"))
    {
      address_history_map = prov.openMutationMapSet("addr_hist_2");
    }
    if (config.getBoolean("tx_index"))
    {
      transaction_block_map = prov.openMutationMapSet("tx_blk_map");
    }
  }

  @Override
  public ProtoDBMap<Block> getBlockMap(){return block_map; }

  @Override 
  public ProtoDBMap<BlockSummary> getBlockSummaryMap(){return block_summary_map; }

  @Override
  public ProtoDBMap<Transaction> getTransactionMap(){return tx_map; }

  @Override
  public DBMap getSpecialMap() { return special_map; }

  @Override
  public DBMap getUtxoNodeMap() { return utxo_node_map; }

  @Override
  public DBMapMutationSet getAddressHistoryMap() { return address_history_map; }

  @Override
  public DBMapMutationSet getSpecialMapSet() { return special_map_set; }

  @Override
  public DBMapMutationSet getTransactionBlockMap() { return transaction_block_map; }

  @Override
  public ChainHash getBlockHashAtHeight(int height)
  {
    ByteBuffer bb = ByteBuffer.allocate(4);
    bb.putInt(height);
    ByteString hash = block_height_map.get(ByteString.copyFrom(bb.array()));
    if (hash == null) return null;

    return new ChainHash(hash);
  }

  @Override
  public void setBlockHashAtHeight(int height, ChainHash hash)
  {
    ByteBuffer bb = ByteBuffer.allocate(4);
    bb.putInt(height);

    block_height_map.put(ByteString.copyFrom(bb.array()), hash.getBytes());
  }


  protected void dbShutdownHandler()
  {
    close();
  }

  protected synchronized Executor getExec()
  {
    if (exec == null)
    {
      exec = new ThreadPoolExecutor(
        32,
        32,
        2, TimeUnit.DAYS,
        new LinkedBlockingQueue<Runnable>(),
        new DaemonThreadFactory("db_exec"));
    }
    return exec;

  }

  public class DBShutdownThread extends Thread
  {
    public DBShutdownThread()
    {
      setName("DBShutdownHandler");
    }

    public void run()
    {
      try
      {
        dbShutdownHandler();
      }
      catch(Throwable t)
      {
        logger.log(Level.WARNING, "Exception in DB shutdown", t);
        t.printStackTrace();
      }
    }

  }
}
