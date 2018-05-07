package snowblossom.db;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import java.util.LinkedList;
import java.text.DecimalFormat;
import com.google.protobuf.ByteString;

import java.util.AbstractMap.SimpleEntry;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import snowblossom.DaemonThreadFactory;
import snowblossom.Config;

import snowblossom.proto.Block;
import snowblossom.proto.BlockSummary;

import java.util.logging.Logger;
import java.util.logging.Level;

public abstract class DB implements DBFace
{
  private static final Logger logger = Logger.getLogger("SnowblossomDB");
  protected int max_set_return_count=100000;

  protected Executor exec;
  protected ProtoDBMap<Block> block_map; 
  protected ProtoDBMap<BlockSummary> block_summary_map; 
  protected DBMap utxo_node_map;

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
    block_summary_map = new ProtoDBMap(BlockSummary.newBuilder().build().getParserForType(), openMap("blocksummary"));

    utxo_node_map = openMap("u");
  }

  @Override
  public ProtoDBMap<Block> getBlockMap(){return block_map; }

  @Override 
  public ProtoDBMap<BlockSummary> getBlockSummaryMap(){return block_summary_map; }

  @Override
  public DBMap getUtxoNodeMap() { return utxo_node_map; }

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
