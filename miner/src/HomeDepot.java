package snowblossom.miner;

import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import duckutil.RateReporter;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.mining.proto.*;
import snowblossom.mining.proto.MiningPoolServiceGrpc.MiningPoolServiceStub;
import snowblossom.mining.proto.MiningPoolServiceGrpc.MiningPoolServiceBlockingStub;
import snowblossom.lib.trie.HashUtils;

import java.io.File;
import java.io.FileInputStream;
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.MinMaxPriorityQueue;
import org.junit.Assert;

import io.grpc.Server;
import io.grpc.ServerBuilder;


public class HomeDepot
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  protected volatile WorkUnit last_work_unit;

  private MiningPoolServiceStub asyncStub;
  protected MiningPoolServiceBlockingStub blockingStub;

  private final NetworkParams params;

  private Config config;


  public HomeDepot(Config config) throws Exception
  {
    this.config = config;

    config.require("pool_host");

    params = NetworkParams.loadFromConfig(config);

    if ((!config.isSet("mine_to_address")) && (!config.isSet("mine_to_wallet")))
    {
      throw new RuntimeException("Config must either specify mine_to_address or mine_to_wallet");
    }
    if ((config.isSet("mine_to_address")) && (config.isSet("mine_to_wallet")))
    {
      throw new RuntimeException("Config must either specify mine_to_address or mine_to_wallet, not both");
    }
    subscribe();
  }

  private ManagedChannel channel;

  private void subscribe() throws Exception
  {
    if (channel != null)
    {
      channel.shutdownNow();
      channel = null;
    }

    String host = config.get("pool_host");
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
      File wallet_db = new File(wallet_path, "wallet.db");

      FileInputStream in = new FileInputStream(wallet_db);
      WalletDatabase wallet = WalletDatabase.parseFrom(in);
      in.close();
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

  public WorkUnit getWorkUnit()
  {
    return last_work_unit;
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
      int min_field = wu.getHeader().getSnowField();
      if (min_field > selected_field)
      {
        logger.log(Level.WARNING, String.format("Configured selected_field %d is less than required field %d", selected_field, min_field));
        last_work_unit = null;
        stop();

        return;
      }
      try
      {
        BlockHeader.Builder bh = BlockHeader.newBuilder();
        bh.mergeFrom(wu.getHeader());
        bh.setSnowField(selected_field);

        WorkUnit wu_new = WorkUnit.newBuilder()
          .mergeFrom(wu)
          .setHeader(bh.build())
          .build();

        last_work_unit = wu_new;

        // If the block number changes, clear the queues
        if (last_block != wu_new.getHeader().getBlockHeight())
        {
          for(FaQueue q : layer_to_queue_map.values())
          {
            q.clear();
          }
        }
      }
      catch (Throwable t)
      {
        logger.info("Work block load error: " + t.toString());
        last_work_unit = null;
      }
    }
  }



}
