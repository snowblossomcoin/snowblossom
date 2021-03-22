package snowblossom.client;

import java.util.Collections;
import java.util.LinkedList;
import java.util.SplittableRandom;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.util.proto.*;

public class LoadTest
{
  private static final Logger logger = Logger.getLogger("snowblossom.client");

  private SnowBlossomClient client;
  public LoadTest(SnowBlossomClient client)
  {
    this.client = client;

  }

  public void runLoadTest()
    throws Exception
  {
    while(true)
    {
      runLoadTestInner();
    }
  }

  private void runLoadTestInner()
    throws Exception
  {
    LinkedList<TransactionBridge> spendable = new LinkedList<>();
    for(TransactionBridge br : client.getAllSpendable())
    {
      if (!br.spent) spendable.add(br);
    }
    Collections.shuffle(spendable);
    long min_send =  50000L;
    long max_send = 500000L;
    long send_delta = max_send - min_send;
    SplittableRandom rnd = new SplittableRandom();

    while(true)
    {
      int output_count = 1;
      long fee = 7500;
      while (rnd.nextDouble() < 0.5) output_count++;
      //Collections.shuffle(spendable);

      LinkedList<TransactionOutput> out_list = new LinkedList<>();
      long needed_value = fee; //should cover a fee
      for(int i=0; i< output_count; i++)
      {
        long value = min_send + rnd.nextLong(send_delta);

        out_list.add( TransactionOutput.newBuilder()
          .setRecipientSpecHash(TransactionUtil.getRandomChangeAddress(client.getPurse().getDB()).getBytes() )
          .setValue(value)
          .build());
        needed_value+=value;
      }

      LinkedList<UTXOEntry> input_list = new LinkedList<>();
      while(needed_value > 0)
      {
        // This can happen because we are accumulating inputs as needed
        // but if the transaction maker uses the inputs in a different order
        // it might not use them all so we end up popping one and not using it
        // and thus forgetting about it.
        if (spendable.size() == 0)
        {
          logger.info("Out of inputs, resyncing");
          return;
        }
        TransactionBridge b = spendable.pop();
        needed_value -= b.value;
        input_list.add(b.toUTXOEntry());
      }

      TransactionFactoryConfig.Builder tx_config = TransactionFactoryConfig.newBuilder();

      tx_config.setSign(true);

      tx_config.addAllOutputs(out_list);
      tx_config.setChangeRandomFromWallet(true);
      tx_config.setInputSpecificList(true);
      tx_config.setFeeUseEstimate(true);
      //tx_config.setFeeUseEstimate(false);
      //tx_config.setFeeFlat(fee);
      tx_config.setSplitChangeOver(25000000L);
      tx_config.addAllInputs(input_list);

      TransactionFactoryResult res = TransactionFactory.createTransaction(tx_config.build(), client.getPurse().getDB(), client);

      for(Transaction tx : res.getTxsList())
      {

        TransactionInner inner = TransactionUtil.getInner(tx);

        ChainHash tx_hash = new ChainHash(tx.getTxHash());
        for(int i=0; i<inner.getOutputsCount(); i++)
        {
          TransactionBridge b = new TransactionBridge(inner.getOutputs(i), i, tx_hash);
          spendable.add(b);
        }

        logger.info("Transaction: " + new ChainHash(tx.getTxHash()) + " - " + tx.toByteString().size());
        TransactionUtil.prettyDisplayTx(tx, System.out, client.getParams());
        //logger.info(tx.toString());

        boolean sent=false;
        while(!sent)
        {
          SubmitReply reply = client.getStub().submitTransaction(tx);
          if (reply.getSuccess())
          {
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
        boolean success = client.submitTransaction(tx);
        System.out.println("Submit: " + success);
        Thread.sleep(100);
        if (!success)
        {
          return;
        }
      }
    }
  }

}
