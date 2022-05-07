package snowblossom.client;

import java.util.Collections;
import java.util.LinkedList;
import java.util.SplittableRandom;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Set;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.util.proto.*;
import io.grpc.stub.StreamObserver;

import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;

import duckutil.RateLimit;
import duckutil.RateReporter;
import java.text.DecimalFormat;


public class LoadTestShard implements StreamObserver<SubmitReply>
{
  private static final Logger logger = Logger.getLogger("snowblossom.client");

  private SnowBlossomClient client;

  private ArrayList<Integer> active_shards;

  private TimeRecord time_record;

  private final boolean use_pending=true;

  private int preferred_shard = -1;
  private ArrayList<Integer> preferred_shards = new ArrayList<>();

  private RateLimit rate_limit = new RateLimit(15.0, 15.0);

	private RateReporter rate_sent = new RateReporter();
	private RateReporter rate_accepted = new RateReporter();
	private RateReporter rate_rejected = new RateReporter();

  public LoadTestShard(SnowBlossomClient client)
  {
    this.client = client;
    FeeEstimate fee_estimate = client.getFeeEstimate();
    active_shards = new ArrayList();

    NodeStatus ns = client.getNodeStatus();
    active_shards.addAll(ns.getNetworkActiveShardsList());

    double rate = client.getConfig().getDoubleWithDefault("loadtest_send_rate", 10.0);

    DecimalFormat df = new DecimalFormat("0.00");
    System.out.println("Running with send rate (tps): " + df.format(rate));

    rate_limit = new RateLimit(rate, 15.0);

    System.out.println("Active Shards: " + active_shards);

    // get list of active shards in a better way
    //active_shards.addAll( fee_estimate.getShardMap().keySet() );
          
    time_record = new TimeRecord();
    TimeRecord.setSharedRecord(time_record);

    if (client.getConfig().isSet("preferred_shard"))
    {
      preferred_shard = client.getConfig().getInt("preferred_shard");
    }

  }

  public void runLoadTest()
    throws Exception
  {
    while(true)
    {
      try
      {
        runLoadTestInner();
      }
      catch(Exception e)
      {
        logger.info("Exception: " + e);
        Thread.sleep(15000);
      }
    }
  }

  private boolean trySend(TreeMap<Integer, LinkedList<TransactionBridge>> spendable_map, SplittableRandom rnd )
    throws Exception
  {
    try(TimeRecordAuto tra_rate = TimeRecord.openAuto("LoadTestShard.rate_limit"))
    {
      rate_limit.waitForRate(1.0);
    }

    try(TimeRecordAuto tra_sendone = TimeRecord.openAuto("LoadTestShard.send_one"))
    {
      long min_send =  5000L;
      long max_send = 50000L;
      long send_delta = max_send - min_send;
      int output_count = 1;
      long fee = 12500;
      while (rnd.nextDouble() < 0.5) output_count++;

      LinkedList<TransactionOutput> out_list = new LinkedList<>();
      long total_out = fee;
      for(int i=0; i< output_count; i++)
      {
        long value = min_send + rnd.nextLong(send_delta);

        int dst_shard = active_shards.get( rnd.nextInt(active_shards.size() ) );
        if (preferred_shard >= 0)
        {
          if (rnd.nextDouble() < 0.95)
          {
            dst_shard = preferred_shard;
            if (preferred_shards.size() > 0)
            {
              dst_shard = preferred_shards.get(rnd.nextInt(preferred_shards.size()));
            }

          }
        }

        out_list.add( TransactionOutput.newBuilder()
          .setRecipientSpecHash(TransactionUtil.getRandomChangeAddress(client.getPurse().getDB()).getBytes() )
          .setValue(value)
          .setTargetShard(dst_shard)
          .build());
        total_out += value;
      }

      ArrayList<Integer> source_shards = new ArrayList<>();
      source_shards.addAll(spendable_map.keySet());
      Collections.shuffle(source_shards);
      int source_shard = source_shards.get(0);

      long needed_funds = total_out;        
      TransactionFactoryConfig.Builder tx_config = TransactionFactoryConfig.newBuilder();
      LinkedList<TransactionBridge> source_list = spendable_map.get(source_shard);

      while(needed_funds > 0L)
      {
        if (source_list.size() ==0)
        {  // Ran out on this shard input
          spendable_map.remove(source_shard);
          return false;
        }
        TransactionBridge br = source_list.pop();

        tx_config.addInputs(br.toUTXOEntry());
        needed_funds -= br.value;
      }
      


      tx_config.setSign(true);

      tx_config.addAllOutputs(out_list);
      tx_config.setInputSpecificList(true);
      tx_config.setChangeRandomFromWallet(true);
      tx_config.setFeeUseEstimate(true);
      tx_config.setSplitChangeOver(2500000L);
      tx_config.setChangeShardId( active_shards.get( rnd.nextInt(active_shards.size()) ) );
      if (preferred_shard >= 0)
      {
        tx_config.setChangeShardId( preferred_shard );
        if (preferred_shards.size() > 0)
        {
          tx_config.setChangeShardId(preferred_shards.get(rnd.nextInt(preferred_shards.size())));
        }


      }
      else
      {
        tx_config.setChangeShardId( active_shards.get( rnd.nextInt(active_shards.size()) ) );
      }

      TransactionFactoryResult res = TransactionFactory.createTransaction(tx_config.build(), client.getPurse().getDB(), client);

      for(Transaction tx : res.getTxsList())
      {
        TransactionInner inner = TransactionUtil.getInner(tx);

        ChainHash tx_hash = new ChainHash(tx.getTxHash());

        //logger.info("Transaction: " + new ChainHash(tx.getTxHash()) + " - " + tx.toByteString().size());
        //TransactionUtil.prettyDisplayTx(tx, System.out, client.getParams());

        client.getUTXOUtil().cacheTransaction(tx);

        try(TimeRecordAuto tra_submit = TimeRecord.openAuto("LoadTestShard.submitAsync"))
        {
          client.getAsyncStub().submitTransaction(tx, this);
					rate_sent.record(1L);
        }
      }
    }
    return true;
  }

  private void runLoadTestInner()
    throws Exception
  {
    try(TimeRecordAuto tra = TimeRecord.openAuto("LoadTestShard.runLoadTestInner"))
    {
      try(TimeRecordAuto tra_ns = TimeRecord.openAuto("LoadTestShard.nodeStatus"))
      {
        NodeStatus ns = client.getNodeStatus();
        active_shards.clear();
        active_shards.addAll(ns.getNetworkActiveShardsList());

        if (preferred_shard >= 0)
        {
          Set<Integer> pref_children 
            = ShardUtil.getChildrenRecursive(preferred_shard, client.getParams().getMaxShardId());
          preferred_shards.clear();
          for(int s : active_shards)
          {
            if (pref_children.contains(s))
            {
               preferred_shards.add(s);
            }
          }
        }
        logger.info(String.format("Active shards: %s Preferred shards: %s",active_shards, preferred_shards));
      }
      SplittableRandom rnd = new SplittableRandom();

      TreeMap<Integer, LinkedList<TransactionBridge>> spendable_map = new TreeMap<>();
      long usable_count = 0;

      for(TransactionBridge br : client.getAllSpendable())
      {
        if (!br.spent)
        if ((use_pending) || (br.isConfirmed()))
        {
          if (!spendable_map.containsKey(br.shard_id))
          {
            spendable_map.put(br.shard_id, new LinkedList<TransactionBridge>());
          }
          spendable_map.get(br.shard_id).add(br);
          usable_count++;
        }
      }

      //Shuffle
      for(LinkedList<TransactionBridge> lst : spendable_map.values()) Collections.shuffle(lst);
      logger.info(String.format("  Usable outputs to spend: %d", usable_count));


      int sent = 0;
      long start_send_time = System.currentTimeMillis();
      long end_send_time = start_send_time + 600L * 1000L;
      while(spendable_map.size() > 0)
      {
        if (trySend(spendable_map, rnd)) sent++;
        if (sent >= 5000) break;
        if (System.currentTimeMillis() > end_send_time) break;
      }
      if (sent==0)
      {
        System.out.println("Unable to send any, sleeping");
        Thread.sleep(5000);
      }

    }
    finally
    {
      System.out.println("-----------------------------------------------");
      time_record.printReport(System.out);
      time_record.reset();
      DecimalFormat df = new DecimalFormat("0.0");

      System.out.println("Sent: " + rate_sent.getReportShort(df));
      System.out.println("Accepted: " + rate_accepted.getReportShort(df));
      System.out.println("Rejected: " + rate_rejected.getReportShort(df));
    }
    
  }

	@Override
  public void onNext(SubmitReply rep)
  {
		if (rep.getSuccess())
		{
			rate_accepted.record(1L);
		}
		else
		{
			rate_rejected.record(1L);
		}
		
  }

	@Override
  public void onCompleted()
  {

  }

	@Override
  public void onError(Throwable t)
  {
		rate_rejected.record(1L);
    t.printStackTrace();
  }

}
