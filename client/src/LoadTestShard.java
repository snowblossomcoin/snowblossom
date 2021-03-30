package snowblossom.client;

import java.util.Collections;
import java.util.LinkedList;
import java.util.SplittableRandom;
import java.util.logging.Logger;
import java.util.ArrayList;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.util.proto.*;

import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;


public class LoadTestShard
{
  private static final Logger logger = Logger.getLogger("snowblossom.client");

  private SnowBlossomClient client;

  private ArrayList<Integer> active_shards;

  private TimeRecord time_record;
  public LoadTestShard(SnowBlossomClient client)
  {
    this.client = client;
    FeeEstimate fee_estimate = client.getFeeEstimate();
    active_shards = new ArrayList();
    active_shards.addAll( fee_estimate.getShardMap().keySet() );
    time_record = new TimeRecord();
    TimeRecord.setSharedRecord(time_record);

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

  private void runLoadTestInner()
    throws Exception
  {
    long min_send =  50000L;
    long max_send = 500000L;
    long send_delta = max_send - min_send;
    SplittableRandom rnd = new SplittableRandom();

    while(true)
    {
      try(TimeRecordAuto tra_sendone = TimeRecord.openAuto("LoadTestShard.send_one"))
      {
        int output_count = 1;
        long fee = 7500;
        while (rnd.nextDouble() < 0.5) output_count++;

        LinkedList<TransactionOutput> out_list = new LinkedList<>();
        for(int i=0; i< output_count; i++)
        {
          long value = min_send + rnd.nextLong(send_delta);
          int dst_shard = active_shards.get( rnd.nextInt(active_shards.size() ) );

          out_list.add( TransactionOutput.newBuilder()
            .setRecipientSpecHash(TransactionUtil.getRandomChangeAddress(client.getPurse().getDB()).getBytes() )
            .setValue(value)
            .setTargetShard(dst_shard)
            .build());
        }


        TransactionFactoryConfig.Builder tx_config = TransactionFactoryConfig.newBuilder();

        tx_config.setSign(true);

        tx_config.addAllOutputs(out_list);
        tx_config.setChangeRandomFromWallet(true);
        //tx_config.setInputConfirmedOnly(true);
        tx_config.setInputConfirmedThenPending(true);
        tx_config.setFeeUseEstimate(true);
        tx_config.setSplitChangeOver(25000000L);
        tx_config.setChangeShardId( active_shards.get( rnd.nextInt(active_shards.size() ) ) );

        TransactionFactoryResult res = TransactionFactory.createTransaction(tx_config.build(), client.getPurse().getDB(), client);

        for(Transaction tx : res.getTxsList())
        {
          TransactionInner inner = TransactionUtil.getInner(tx);

          ChainHash tx_hash = new ChainHash(tx.getTxHash());

          logger.info("Transaction: " + new ChainHash(tx.getTxHash()) + " - " + tx.toByteString().size());
          TransactionUtil.prettyDisplayTx(tx, System.out, client.getParams());

          boolean sent=false;
          while(!sent)
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
                return;
              }
            }

          }
          //Thread.sleep(100);
        }
      }
      finally
      {

        time_record.printReport(System.out);
        time_record.reset();
      }

    }
  }

}
