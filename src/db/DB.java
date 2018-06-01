package snowblossom.db;

import com.google.protobuf.ByteString;
import duckutil.Config;
import snowblossom.ChainHash;
import snowblossom.DaemonThreadFactory;
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

public abstract class DB implements DBFace
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

  public DB(Config config)
  {
    Runtime.getRuntime().addShutdownHook(new DBShutdownThread());
  }


  public void close()
  {
  }

  public void open()
    throws Exception
  {
    block_map = new ProtoDBMap(Block.newBuilder().build().getParserForType(), openMap("block"));
    tx_map = new ProtoDBMap(Transaction.newBuilder().build().getParserForType(), openMap("tx"));
    block_summary_map = new ProtoDBMap(BlockSummary.newBuilder().build().getParserForType(), openMap("blocksummary"));

    utxo_node_map = openMap("u");
    block_height_map = openMap("height");
    special_map = openMap("special");
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


  protected abstract DBMap openMap(String name) throws Exception;
  protected abstract DBMapMutationSet openMutationMapSet(String name) throws Exception;

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
