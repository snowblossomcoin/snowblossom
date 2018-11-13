package snowblossom.miner;

import com.google.protobuf.ByteString;
import duckutil.Config;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.mining.proto.*;
import snowblossom.mining.proto.MiningPoolServiceGrpc.MiningPoolServiceStub;
import snowblossom.mining.proto.MiningPoolServiceGrpc.MiningPoolServiceBlockingStub;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.Queue;
import java.util.Map;
import java.io.File;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.MinMaxPriorityQueue;
import org.junit.Assert;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import snowblossom.client.WalletUtil;


/**
 * Maintains connections with a list of pools.  Supports pool failover.
 */
public class PoolClient implements PoolClientFace
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  private volatile WorkUnit last_work_unit;

  private MiningPoolServiceStub asyncStub;
  protected MiningPoolServiceBlockingStub blockingStub;

  private final NetworkParams params;
  private Config config;
  private PoolClientOperator op;
  private String host;

  public PoolClient(Config config, PoolClientOperator op) throws Exception
  {
    this(config.get("pool_host"), config, op);

  }


  public PoolClient(String host, Config config, PoolClientOperator op) throws Exception
  {
    this.host = host;
    this.config = config;
    this.op = op;

    params = NetworkParams.loadFromConfig(config);

    if ((!config.isSet("mine_to_address")) && (!config.isSet("mine_to_wallet")))
    {
      throw new RuntimeException("Config must either specify mine_to_address or mine_to_wallet");
    }
    if ((config.isSet("mine_to_address")) && (config.isSet("mine_to_wallet")))
    {
      throw new RuntimeException("Config must either specify mine_to_address or mine_to_wallet, not both");
    }

  }

  private ManagedChannel channel;


	@Override
  public void subscribe() throws Exception
  {
    if (channel != null)
    {
      channel.shutdownNow();
      channel = null;
    }

    int port = config.getIntWithDefault("pool_port", 23380);
    channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();

    asyncStub = MiningPoolServiceGrpc.newStub(channel);
    blockingStub = MiningPoolServiceGrpc.newBlockingStub(channel);

    AddressSpecHash to_addr = getMineToAddress();
    String address_str = AddressUtil.getAddressString(params.getAddressPrefix(), to_addr);

    String client_id = null;
    if (config.isSet("mining_client_id"))
    {
      client_id = config.get("mining_client_id");
    }
    GetWorkRequest.Builder req = GetWorkRequest.newBuilder();
    if (client_id != null) req.setClientId(client_id);

    req.setPayToAddress(address_str);

    asyncStub.getWork( req.build(), new WorkUnitEater());
    logger.info("Subscribed to work");

  }

  private AddressSpecHash getMineToAddress() throws Exception
  {

    if (config.isSet("mine_to_address"))
    {
      String address = config.get("mine_to_address");
      AddressSpecHash to_addr = new AddressSpecHash(address, params);
      return to_addr;
    }
    if (config.isSet("mine_to_wallet"))
    {
      File wallet_path = new File(config.get("mine_to_wallet"));
      if (!wallet_path.isDirectory())
      {
        throw new RuntimeException("Wallet directory " + wallet_path + " does not exist");
      }
      WalletDatabase wallet = WalletUtil.loadWallet(wallet_path, false, params);
      if (wallet == null)
      {
        throw new RuntimeException("No wallet found in directory " + wallet_path);
      }

      if (wallet.getAddressesCount() == 0)
      {
        throw new RuntimeException("Wallet has no addresses");
      }
      LinkedList<AddressSpec> specs = new LinkedList<AddressSpec>();
      specs.addAll(wallet.getAddressesList());
      Collections.shuffle(specs);

      AddressSpec spec = specs.get(0);
      AddressSpecHash to_addr = AddressUtil.getHashForSpec(spec);
      return to_addr;
    }
    return null;
  }


	@Override
  public void stop()
  {
    terminate = true;
  }

	@Override
  public boolean isTerminated()
  {
    return terminate;
  }

  private volatile boolean terminate = false;

	@Override
  public WorkUnit getWorkUnit()
  {
    WorkUnit wu = last_work_unit;
    if (wu == null) return null;
    if (wu.getHeader().getTimestamp() + 45000 < System.currentTimeMillis())
    {
      return null;
    }

    return wu;
  }

  public class WorkUnitEater implements StreamObserver<WorkUnit>
  {
    public void onCompleted() {}
    public void onError(Throwable t) 
    {
      logger.info("Error talking to mining pool: " + t);
    }
    public void onNext(WorkUnit wu)
    {
      int last_block = -1;
      WorkUnit wu_old = last_work_unit;
      if (wu_old != null)
      {
        last_block = wu_old.getHeader().getBlockHeight();

      }
      
      last_work_unit = wu;
      op.notifyNewWorkUnit(wu);

      // If the block number changes, clear the queues
      if (last_block != wu.getHeader().getBlockHeight())
      {
        op.notifyNewBlock(wu.getHeader().getBlockHeight());
      }
      
    }
  }

	@Override
  public SubmitReply submitWork(WorkUnit wu, BlockHeader header)
  {
       WorkSubmitRequest.Builder req = WorkSubmitRequest.newBuilder();
      req.setWorkId(wu.getWorkId());
      req.setHeader(header);

      SubmitReply reply = blockingStub.submitWork( req.build());
		return reply;
 

  }

}
