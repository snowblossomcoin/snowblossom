package snowblossom.client;

import java.util.Collections;
import java.util.LinkedList;
import java.util.SplittableRandom;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.TreeMap;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.util.proto.*;

import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;

import duckutil.RateLimit;


public class LoadTestShard
{
  private static final Logger logger = Logger.getLogger("snowblossom.client");

  private SnowBlossomClient client;

  private ArrayList<Integer> active_shards;

  private TimeRecord time_record;

  private final boolean use_pending=true;

  private int preferred_shard = -1;
  private RateLimit rate_limit = new RateLimit(8.0, 15.0);

  public LoadTestShard(SnowBlossomClient client)
  {
    this.client = client;
    FeeEstimate fee_estimate = client.getFeeEstimate();
    active_shards = new ArrayList();
    active_shards.addAll( fee_estimate.getShardMap().keySet() );
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
          if (rnd.nextDouble() < 0.75) dst_shard=preferred_shard;
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

        logger.info("Transaction: " + new ChainHash(tx.getTxHash()) + " - " + tx.toByteString().size());
        TransactionUtil.prettyDisplayTx(tx, System.out, client.getParams());

        client.getUTXOUtil().cacheTransaction(tx);

        boolean sent=false;
        while(!sent)
        {
          try(TimeRecordAuto tra_submit = TimeRecord.openAuto("LoadTestShard.submit"))
          {
            SubmitReply reply = client.getStub().submitTransaction(tx);
            
            if (reply.getSuccess())
            {
              
              logger.info("Submit: " + reply);
              sent=true;

            }
            else
            {
              logger.info("Error: " + reply.getErrorMessage());
              if (reply.getErrorMessage().contains("full"))
              {
                Thread.sleep(60000);
              }
              else
              {
                return false;
              }
            }
          }

        }
        //Thread.sleep(100);
      }
    }
    return true;
  }

  private void runLoadTestInner()
    throws Exception
  {
    try(TimeRecordAuto tra_sendone = TimeRecord.openAuto("LoadTestShard.runLoadTestInner"))
    {
      SplittableRandom rnd = new SplittableRandom();

      TreeMap<Integer, LinkedList<TransactionBridge>> spendable_map = new TreeMap<>();

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
        }
      }

      //Shuffle
      for(LinkedList<TransactionBridge> lst : spendable_map.values()) Collections.shuffle(lst);


      int sent = 0;
      while(spendable_map.size() > 0)
      {
        if (trySend(spendable_map, rnd)) sent++;
      }
      if (sent==0)
      {
        System.out.println("Unable to send any, sleeping");
        Thread.sleep(5000);
      }

    }
    finally
    {
      time_record.printReport(System.out);
      time_record.reset();
    }
    
  }

}
