package systemtests.test;

import com.google.protobuf.ByteString;
import java.io.File;
import java.util.*;
import org.junit.Assert;
import org.junit.Test;
import snowblossom.client.SnowBlossomClient;
import snowblossom.client.TransactionFactory;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.Globals;
import snowblossom.miner.SnowBlossomMiner;
import snowblossom.node.ForBenefitOfUtil;
import snowblossom.node.SnowBlossomNode;
import snowblossom.proto.*;
import snowblossom.util.proto.*;

public class SpoonTestFbo extends SpoonTest
{


  @Test
  public void fboTest() throws Exception
  {
    File snow_path = setupSnow();

    Random rnd = new Random();
    int port = 20000 + rnd.nextInt(30000);
    SnowBlossomNode node = startNode(port);
    Thread.sleep(100);

    SnowBlossomClient client = startClientWithWallet(port);
    SnowBlossomClient client_lock = startClientWithWallet(port);

    WalletDatabase lock_db = genWallet();
    WalletDatabase social_db = genWallet();

    AddressSpecHash mine_to_addr = client.getPurse().getUnusedAddress(false,false);
    AddressSpecHash lock_to_addr = client_lock.getPurse().getUnusedAddress(false, false);

    AddressSpecHash social_addr = AddressUtil.getHashForSpec( social_db.getAddresses(0) );


    SnowBlossomMiner miner = startMiner(port, mine_to_addr, snow_path);

    testMinedBlocks(node);


    LinkedList<Transaction> tx_list = new LinkedList<>();
    LinkedList<Transaction> tx_list_jumbo = new LinkedList<>();
    LinkedList<Transaction> tx_list_swoopo = new LinkedList<>();

    for(int i=0; i<10; i++)
    {
      TransactionFactoryConfig.Builder config = TransactionFactoryConfig.newBuilder();

      config.setSign(true);
      config.setChangeRandomFromWallet(true);
      config.setInputConfirmedThenPending(true);
      config.setFeeUseEstimate(true);
      config.addOutputs( TransactionOutput.newBuilder()
        .setValue( Globals.SNOW_VALUE)
        .setRecipientSpecHash(lock_to_addr.getBytes())
        .setForBenefitOfSpecHash(social_addr.getBytes())
        .build());
      config.addOutputs( TransactionOutput.newBuilder()
        .setValue( Globals.SNOW_VALUE)
        .setRecipientSpecHash(lock_to_addr.getBytes())
        .setForBenefitOfSpecHash(social_addr.getBytes())
        .setIds( ClaimedIdentifiers.newBuilder().setUsername( ByteString.copyFrom("jumbo".getBytes())) )
        .build());
      config.addOutputs( TransactionOutput.newBuilder()
        .setValue( Globals.SNOW_VALUE)
        .setRecipientSpecHash(lock_to_addr.getBytes())
        .setForBenefitOfSpecHash(social_addr.getBytes())
        .setIds( ClaimedIdentifiers.newBuilder().setChannelname( ByteString.copyFrom("swoopo".getBytes())) )
        .build());
      config.addOutputs( TransactionOutput.newBuilder()
        .setValue( Globals.SNOW_VALUE)
        .setRecipientSpecHash(lock_to_addr.getBytes())
        .setForBenefitOfSpecHash(social_addr.getBytes())
        .setIds( ClaimedIdentifiers.newBuilder().setUsername( ByteString.copyFrom(("name-" + i).getBytes())) )
        .build());

      TransactionFactoryResult tr = TransactionFactory.createTransaction(config.build(), client.getPurse().getDB(), client);

      for(Transaction tx : tr.getTxsList())
      {
        SubmitReply submit = client.getStub().submitTransaction(tx);
        System.out.println(submit);

        Assert.assertTrue(submit.getErrorMessage(), submit.getSuccess());
      }

      tx_list.addAll(tr.getTxsList());

      waitForMoreBlocks(node, 1);

    }


    // TODO - test FBO
    TxOutList fbo_out_list = ForBenefitOfUtil.getFBOList(social_addr,
      node.getDB(),
      node.getBlockIngestor().getHead());

    Assert.assertEquals( 40, fbo_out_list.getOutListCount());

    // TODO - test user name
    TxOutList jumbo_list = ForBenefitOfUtil.getIdListUser(ByteString.copyFrom("jumbo".getBytes()),
      node.getDB(),
      node.getBlockIngestor().getHead());
    Assert.assertEquals( 10, jumbo_list.getOutListCount());

    // TODO - test channel name
    TxOutList swoopo_list = ForBenefitOfUtil.getIdListChannel(ByteString.copyFrom("swoopo".getBytes()),
      node.getDB(),
      node.getBlockIngestor().getHead());
    Assert.assertEquals( 10, swoopo_list.getOutListCount());

    // Make sure the order of these matches the order put onto the chain
    // So the first is the oldest
    for(int i = 0; i<tx_list.size(); i++)
    {
      ByteString tx = tx_list.get(i).getTxHash();
      Assert.assertEquals(tx, jumbo_list.getOutList(i).getTxHash());
      Assert.assertEquals(tx, swoopo_list.getOutList(i).getTxHash());

      Assert.assertEquals(1, ForBenefitOfUtil.getIdListUser(
        ByteString.copyFrom(("name-" + i).getBytes()),
        node.getDB(),
        node.getBlockIngestor().getHead()).getOutListCount());

    }

    { // Send back - spend all
      TransactionFactoryConfig.Builder config = TransactionFactoryConfig.newBuilder();

      config.setSign(true);
      config.setChangeRandomFromWallet(true);
      config.setInputConfirmedThenPending(true);
      config.setFeeUseEstimate(true);
      config.setSendAll(true);
      config.addOutputs( TransactionOutput.newBuilder()
        .setValue( 0L )
        .setRecipientSpecHash(mine_to_addr.getBytes())
        .build());

      TransactionFactoryResult tr = TransactionFactory.createTransaction(config.build(), client_lock.getPurse().getDB(), client_lock);

      for(Transaction tx : tr.getTxsList())
      {
        SubmitReply submit = client_lock.getStub().submitTransaction(tx);
        System.out.println(submit);

        Assert.assertTrue(submit.getErrorMessage(), submit.getSuccess());
      }

      waitForMoreBlocks(node, 1);

    }

    // TODO - test FBO
    TxOutList fbo_out_list_a = ForBenefitOfUtil.getFBOList(social_addr,
      node.getDB(),
      node.getBlockIngestor().getHead());
    Assert.assertEquals( 0, fbo_out_list_a.getOutListCount());

    // TODO - test user name
    TxOutList jumbo_list_a = ForBenefitOfUtil.getIdListUser(ByteString.copyFrom("jumbo".getBytes()),
      node.getDB(),
      node.getBlockIngestor().getHead());
    Assert.assertEquals( 0, jumbo_list_a.getOutListCount());

    // TODO - test channel name
    TxOutList swoopo_list_a = ForBenefitOfUtil.getIdListChannel(ByteString.copyFrom("swoopo".getBytes()),
      node.getDB(),
      node.getBlockIngestor().getHead());
    Assert.assertEquals( 0, swoopo_list_a.getOutListCount());


    miner.stop();
    node.stop();




  }


}
