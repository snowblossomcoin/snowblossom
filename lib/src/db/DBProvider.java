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

public abstract class DBProvider
{
  private static final Logger logger = Logger.getLogger("snowblossom.db");

  protected Config config;

  public DBProvider(Config config)
  {
  }


  public void close()
  {
  }

  public abstract DBMap openMap(String name) throws Exception;
  public abstract DBMapMutationSet openMutationMapSet(String name) throws Exception;

}
