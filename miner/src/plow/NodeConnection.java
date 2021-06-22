package snowblossom.miner.plow;

import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import duckutil.PeriodicThread;
import duckutil.TimeRecord;
import duckutil.jsonrpc.JsonRpcServer;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.client.StubUtil;
import snowblossom.client.StubHolder;
import snowblossom.lib.*;
import snowblossom.lib.db.DB;
import snowblossom.lib.db.lobstack.LobstackDB;
import snowblossom.lib.db.rocksdb.JRocksDB;
import snowblossom.lib.db.atomicfile.AtomicFileDB;
import snowblossom.mining.proto.*;
import java.util.HashSet;
import snowblossom.proto.*;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;


/**
 * Handles a connection to a single node for getting block templates
 */
public class NodeConnection extends PeriodicThread implements StreamObserver<BlockTemplate>
{

  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  private final MrPlow mr_plow;
  private final String uri;
  private final NetworkParams params;
  private final StubHolder stub_holder;

  private volatile SubscribeBlockTemplateRequest last_template_req;
  private volatile BlockTemplate last_template;

  private volatile StreamObserver<SubscribeBlockTemplateRequest> template_update_observer;
  private NodeStatus node_status;

  private long last_network;
  
  // If we don't hear about a new block every 45s, assume link is dead
  public final static long MAX_NETWORK_AGE = 45000L; 


  public NodeConnection(MrPlow mr_plow, String uri, NetworkParams params)
  {
    super(15000L, 1000.0);
    this.mr_plow = mr_plow;
    this.uri = uri;
    this.params = params;
    stub_holder = new StubHolder();

    setName("NodeConnection/" + uri);
    setDaemon(true);
  }

  @Override
  public void runPass()
    throws Exception
  {
    // Check health - close as needed
    if (last_network + MAX_NETWORK_AGE < System.currentTimeMillis())
    {
      if (stub_holder.getChannel() != null)
      {
        stub_holder.getChannel().shutdownNow();
      }
      node_status = null;
      last_template = null;
      template_update_observer = null;

      try
      {

        logger.info("Attempting new connection to: " + uri);
        stub_holder.update(StubUtil.openChannel(uri, params));
        node_status = stub_holder.getBlockingStub().getNodeStatus( NullRequest.newBuilder().build() );

        template_update_observer = stub_holder.getAsyncStub().subscribeBlockTemplateStreamExtended(this);
        if (last_template_req != null)
        {
          template_update_observer.onNext(last_template_req);
        }
        last_network = System.currentTimeMillis();
      }
      catch(Throwable t)
      {
        logger.info(String.format("Error connecting to %s - %s", uri, t.toString()));
      }
    }

  }


  /**
   * Should return immediately and not throw anything
   */
  public void updateSubscription(SubscribeBlockTemplateRequest req)
  {
    last_template_req = req;

    StreamObserver<SubscribeBlockTemplateRequest> ob = template_update_observer;
    try
    {
      if (ob != null)
      {
        ob.onNext(req);
      }
    }
    catch(Throwable t)
    {
      logger.info("Error on update subscription: " + t);
      last_network = 0L;
    }
  }

  public void submitBlock(Block blk, StreamObserver<SubmitReply> obs)
  {
    int shard_id = blk.getHeader().getShardId();
    if (node_status == null) return;
    HashSet<Integer> interest = new HashSet<>();
    interest.addAll(node_status.getInterestShardsList());
    if (!interest.contains(shard_id)) return;

    stub_holder.getAsyncStub().submitBlock( blk, obs );

  }

  /**
   * Returns latest block template or null if we don't have one
   */
  public BlockTemplate getLatestBlockTemplate()
  {
    return last_template;
  }

  public void onCompleted() 
  {
  	logger.info("Got onCompleted");
    last_network = 0L;
  }

  public void onError(Throwable t)
  {
  	logger.info("Got error:" + t);
    last_network = 0L;
  }

	public void onNext(BlockTemplate bt)
	{
    last_network = System.currentTimeMillis();

		if (bt.getBlock().getHeader().getVersion() == 0)
		{
			last_template = null;
			logger.info("Got null template from " + uri);
		}
		else
		{
			Block b = bt.getBlock();
      logger.info(String.format("Got block template from %s - s:%d h:%d - tx:%d",
        uri, b.getHeader().getShardId(),
        b.getHeader().getBlockHeight(),
        b.getTransactionsCount()));
			
			last_template = bt;
      mr_plow.updateBlockTemplate();
		}
	}


}
