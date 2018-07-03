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


public class Arktika
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  public static void main(String args[]) throws Exception
  {
    Globals.addCryptoProvider();
    if (args.length != 1)
    {
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: Arktika <config_file>");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0]);

    LogSetup.setup(config);

    Arktika miner = new Arktika(config);

    while (!miner.isTerminated())
    {
      Thread.sleep(15000);
      miner.printStats();
    }
  }

  protected volatile WorkUnit last_work_unit;

  private MiningPoolServiceStub asyncStub;
  protected MiningPoolServiceBlockingStub blockingStub;

  private final NetworkParams params;

  protected AtomicLong op_count = new AtomicLong(0L);
  private long last_stats_time = System.currentTimeMillis();
  private Config config;

  private File snow_path;

  private TimeRecord time_record;
  private RateReporter rate_report=new RateReporter();

  protected AtomicLong share_submit_count = new AtomicLong(0L);
  protected AtomicLong share_reject_count = new AtomicLong(0L);
  protected AtomicLong share_block_count = new AtomicLong(0L);


  private final int selected_field;

  protected FieldSource deck_source;

  private FieldSource all_sources[];
  private ImmutableMap<Integer, Integer> chunk_to_layer_map;
  private ImmutableMap<Integer, Queue<PartialWork> > chunk_to_queue_map;
  private ImmutableMap<Integer, Queue<PartialWork> > layer_to_queue_map;
  protected FieldSource composit_source;
  private final int layer_count;


  public Arktika(Config config) throws Exception
  {
    this.config = config;
    logger.info(String.format("Starting Arktika version %s", Globals.VERSION));

    config.require("pool_host");
    config.require("selected_field");
    config.require("layer_count");

    layer_count = config.getInt("layer_count");

    selected_field = config.getInt("selected_field");

    params = NetworkParams.loadFromConfig(config);

    if ((!config.isSet("mine_to_address")) && (!config.isSet("mine_to_wallet")))
    {
      throw new RuntimeException("Config must either specify mine_to_address or mine_to_wallet");
    }
    if ((config.isSet("mine_to_address")) && (config.isSet("mine_to_wallet")))
    {
      throw new RuntimeException("Config must either specify mine_to_address or mine_to_wallet, not both");
    }

    // this is a bad idea, don't use this.  It eats all the cpu doing
    // record keeping
    if (config.getBoolean("display_timerecord"))
    {
      time_record = new TimeRecord();
      TimeRecord.setSharedRecord(time_record);
    }

    loadField();

    subscribe();

      Server s = ServerBuilder
        .forPort(2311)
        .addService(new Stubo(composit_source))
        .build();
      s.start();



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

  public void stop()
  {
    terminate = true;
  }
  public boolean isTerminated()
  {
    return terminate;
  }

  private volatile boolean terminate = false;

  public void printStats()
  {
    long now = System.currentTimeMillis();
    long count_long = op_count.getAndSet(0L);
    double count = count_long;
    rate_report.record(count_long);

    double time_ms = now - last_stats_time;
    double time_sec = time_ms / 1000.0;
    double rate = count / time_sec;

    DecimalFormat df = new DecimalFormat("0.000");

    String block_time_report = "";
    if (last_work_unit != null)
    {
      BigInteger target = BlockchainUtil.targetBytesToBigInteger(last_work_unit.getReportTarget());

      double diff = PowUtil.getDiffForTarget(target);

      double block_time_sec = Math.pow(2.0, diff) / rate;
      double min = block_time_sec / 60.0;
      block_time_report = String.format("- at this rate %s minutes per share (diff %s)", df.format(min), df.format(diff));
    }


    logger.info(String.format("15 Second mining rate: %s/sec %s", df.format(rate), block_time_report));
    logger.info(rate_report.getReportShort(df));

    last_stats_time = now;

    if (count == 0)
    {
      logger.info("we seem to be stalled, reconnecting to node");
      try
      {
        subscribe();
      }
      catch (Throwable t)
      {
        logger.info("Exception in subscribe: " + t);
      }
    }

    if (config.getBoolean("display_timerecord"))
    {

      TimeRecord old = time_record;

      time_record = new TimeRecord();
      TimeRecord.setSharedRecord(time_record);

      old.printReport(System.out);
    }

    StringBuilder queue_report = new StringBuilder();
    queue_report.append("Queues: {"); 
    for(Map.Entry<Integer, Queue<PartialWork>> me : layer_to_queue_map.entrySet())
    {
      queue_report.append(me.getValue().size());
      queue_report.append(",");
    }
    queue_report.append("}");
    logger.info(queue_report.toString());

    logger.info(String.format("Shares: %d (rejected %d) (blocks %d)", share_submit_count.get(), share_reject_count.get(), share_block_count.get()));
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
          for(Queue<PartialWork> q : layer_to_queue_map.values())
          {
            synchronized(q)
            {
              q.clear();
            }
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


  private void loadField() throws Exception
  {
    for(int i = 0; i<layer_count; i++)
    {
      config.require("layer_" + i + "_threads");
      config.require("layer_" + i + "_type");
    }

    if (layer_count == 0)
    {
      throw new RuntimeException("Time is but a window");
    }

    all_sources=new FieldSource[layer_count];

    List<FieldSource> disk_sources = new LinkedList<>();
    TreeSet<Integer> found = new TreeSet<>();

    // Load up the disk sources first
    // so that memory sources can read from them
    for(int i=0; i<layer_count; i++)
    {
      String type = config.get("layer_" + i + "_type");
      if (type.equals("file"))
      {
        config.require("layer_" + i + "_path");
        String path = config.get("layer_" + i + "_path");
      
        FieldSource fs = null;
        fs = new FieldSourceFile(params, selected_field, new File(path));
        disk_sources.add(fs);
        all_sources[i] = fs;
        for(int x : fs.getHoldingSet())
        {
          if (!found.contains(x))
          {
            found.add(x);
          }
        }
      }
      else if (type.equals("fake"))
      {
        FieldSource fs = new FieldSourceFake(params, selected_field);
        disk_sources.add(fs);
        all_sources[i] = fs;
        for(int x : fs.getHoldingSet())
        {
          if (!found.contains(x))
          {
            found.add(x);
          }
        }
      }
      else if (type.equals("remote"))
      {
        FieldSource fs = new FieldSourceRemote(config, i);
        all_sources[i] = fs;
        for(int x : fs.getHoldingSet())
        {
          if (!found.contains(x))
          {
            found.add(x);
          }
        }
      }
      else if (type.equals("mem"))
      {
        //We'll get to you layer
      }
    }
    
    for(int i=0; i<layer_count; i++)
    {
      String type = config.get("layer_" + i + "_type");
      if (type.equals("mem"))
      {
        config.require("layer_" + i + "_range");
        List<String> range_str = config.getList("layer_"+i+"_range");
        int start = Integer.parseInt(range_str.get(0));
        int end = Integer.parseInt(range_str.get(1));
        logger.info("Mem range: " + start + " to " + end);
        Assert.assertTrue(start >= 0);
        Assert.assertTrue(end > start);

        TreeSet<Integer> mem_set = new TreeSet<>();
        for(int j=start; j<end; j++)
        {
          mem_set.add(j);
        }

        FieldSource fs = new FieldSourceMem(mem_set, disk_sources);
        all_sources[i] = fs;
      }
    }
    logger.info(String.format("Found %d chunks", found.size()));

    TreeMap<Integer, Integer> chunk_to_source_map = new TreeMap<>();
    TreeMap<Integer, Queue<PartialWork> > layer_to_queue=new TreeMap();

    for(int i=0; i<layer_count; i++)
    {
      FieldSource fs = all_sources[i];
      if ((deck_source == null) && (fs.hasDeckFiles()))
      {
        deck_source = fs;
      }
      for(int x : fs.getHoldingSet())
      {
        if (!chunk_to_source_map.containsKey(x))
        {
          chunk_to_source_map.put(x,i);
        }
      }
      layer_to_queue.put(i, MinMaxPriorityQueue.maximumSize(10000).expectedSize(10000).create());

      logger.info(String.format("Layer %d - %s", i, fs.toString()));
    }

    chunk_to_layer_map = ImmutableMap.copyOf(chunk_to_source_map);
    layer_to_queue_map = ImmutableMap.copyOf(layer_to_queue);

    TreeMap<Integer, Queue<PartialWork>> chunk_to_queue=new TreeMap<>();
    for(int x : chunk_to_layer_map.keySet())
    {
      int layer = chunk_to_layer_map.get(x);
      chunk_to_queue.put(x, layer_to_queue_map.get(layer));
    }
    chunk_to_queue_map = ImmutableMap.copyOf(chunk_to_queue);
    
    logger.info(chunk_to_source_map.toString());
    if (deck_source == null)
    {
      throw new RuntimeException("No sources seem to have the deck files.");
    }
    composit_source = new FieldSourceComposit(ImmutableList.copyOf(all_sources));


    long total_words = params.getSnowFieldInfo(selected_field).getLength() / SnowMerkle.HASH_LEN_LONG;
    // START THREADS
    for(int x=0; x<layer_count; x++)
    {
      FieldSource fs = all_sources[x];

      int thread_count = config.getInt("layer_" + x + "_threads");
      for(int i=0; i<thread_count; i++)
      {
        if (fs instanceof BatchSource)
        {
          new BatchLayerWorkThread(this, fs,  layer_to_queue_map.get(x), total_words).start();
        }
        else
        {
          new LayerWorkThread(this, fs, layer_to_queue_map.get(x), total_words).start();
        }
      }
    }

  }

  public void enqueue(int chunk, PartialWork work)
  {
    Assert.assertNotNull(work);

    Queue<PartialWork> q = chunk_to_queue_map.get(chunk);
    if (q == null) return;
    synchronized(q)
    {
      q.offer(work);
      q.notify();
      q.notify();
    }
  }



}
