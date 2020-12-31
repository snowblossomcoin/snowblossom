package systemtests.test;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import duckutil.ConfigMem;
import java.io.File;
import java.security.KeyPair;
import java.util.*;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import snowblossom.client.SnowBlossomClient;
import snowblossom.client.TransactionFactory;
import snowblossom.client.WalletUtil;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.ChainHash;
import snowblossom.lib.Globals;
import snowblossom.lib.KeyUtil;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.NetworkParamsRegtest;
import snowblossom.lib.SignatureUtil;
import snowblossom.lib.SnowFall;
import snowblossom.lib.SnowFieldInfo;
import snowblossom.lib.SnowMerkle;
import snowblossom.lib.TransactionBridge;
import snowblossom.lib.TransactionUtil;
import snowblossom.miner.PoolMiner;
import snowblossom.miner.SnowBlossomMiner;
import snowblossom.miner.plow.MrPlow;
import snowblossom.node.AddressHistoryUtil;
import snowblossom.node.ForBenefitOfUtil;
import snowblossom.node.SnowBlossomNode;
import snowblossom.node.TransactionMapUtil;
import snowblossom.proto.*;
import snowblossom.util.proto.*;

public class SpoonTest
{
  @Rule
  public TemporaryFolder test_folder = new TemporaryFolder();

  @BeforeClass
  public static void loadProvider()
  {
    Globals.addCryptoProvider();
  }

  /**
   * More of a giant orbital space platform full of weasels
   * than a unit test
   */
  @Test
  public void spoonTest() throws Exception
  {
    File snow_path = setupSnow();

    Random rnd = new Random();
    int port = 20000 + rnd.nextInt(30000);
    SnowBlossomNode node = startNode(port);
    Thread.sleep(100);

    KeyPair key_pair = KeyUtil.generateECCompressedKey();
    AddressSpec claim = AddressUtil.getSimpleSpecForKey(key_pair.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);
    AddressSpecHash to_addr = AddressUtil.getHashForSpec(claim);

    SnowBlossomMiner miner = startMiner(port, to_addr, snow_path);

    testMinedBlocks(node);

    SnowBlossomClient client = startClient(port);
    testConsolidateFunds(node, client, key_pair, to_addr);

    SnowBlossomNode node2 = startNode(port + 1);
    node2.getPeerage().connectPeer("localhost", port);
    testMinedBlocks(node2);


    miner.stop();
    Thread.sleep(500);
    node.stop();
    node2.stop();
  }

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

      SubmitReply submit = client.getStub().submitTransaction(tr.getTx());
      System.out.println(submit);

      Assert.assertTrue(submit.getErrorMessage(), submit.getSuccess());

      tx_list.add(tr.getTx());

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

      SubmitReply submit = client_lock.getStub().submitTransaction(tr.getTx());
      System.out.println(submit);

      Assert.assertTrue(submit.getErrorMessage(), submit.getSuccess());

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

  @Test
  public void spoonPoolTest() throws Exception
  {
    File snow_path = setupSnow();

    Random rnd = new Random();
    int port = 20000 + rnd.nextInt(30000);
    SnowBlossomNode node = startNode(port);
    Thread.sleep(100);

    KeyPair key_pair = KeyUtil.generateECCompressedKey();
    AddressSpec claim = AddressUtil.getSimpleSpecForKey(key_pair.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);
    AddressSpecHash to_addr = AddressUtil.getHashForSpec(claim);

    KeyPair key_pair2 = KeyUtil.generateECCompressedKey();
    AddressSpec claim2 = AddressUtil.getSimpleSpecForKey(key_pair2.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);
    AddressSpecHash to_addr2 = AddressUtil.getHashForSpec(claim2);

    KeyPair key_pair3 = KeyUtil.generateECCompressedKey();
    AddressSpec claim3 = AddressUtil.getSimpleSpecForKey(key_pair3.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);
    AddressSpecHash to_addr3 = AddressUtil.getHashForSpec(claim3);

    SnowBlossomClient client = startClient(port);

    MrPlow plow = startMrPlow(port, to_addr2);

    PoolMiner miner = startPoolMiner(port+1, to_addr, snow_path);

    waitForMoreBlocks(node, 10);

    System.out.println("ShareMap: " + plow.getShareManager().getShareMap());
    System.out.println("ShareMap pay: " + plow.getShareManager().getPayRatios());

    // Pool getting paid
    waitForFunds(client, to_addr2, 10);

    // Miner getting paid
    waitForFunds(client, to_addr, 30);

    PoolMiner miner2 = startPoolMiner(port+1, to_addr3, snow_path);

    // Second miner getting paid
    waitForFunds(client, to_addr3, 30);
    
    miner.stop();
    miner2.stop();
    Thread.sleep(500);
    plow.stop();
    node.stop();

  }


  @Test
  public void networkReconsileTest() throws Exception
  {
    File snow_path = setupSnow();

    Random rnd = new Random();
    int port = 20000 + rnd.nextInt(30000);
    SnowBlossomNode node1 = startNode(port);
    SnowBlossomNode node2 = startNode(port + 1);
    Thread.sleep(100);

    KeyPair key_pair = KeyUtil.generateECCompressedKey();

    AddressSpec claim = AddressUtil.getSimpleSpecForKey(key_pair.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);

    AddressSpecHash to_addr = AddressUtil.getHashForSpec(claim);

    SnowBlossomMiner miner1 = startMiner(port, to_addr, snow_path);
    SnowBlossomMiner miner2 = startMiner(port + 1, to_addr, snow_path);

    testMinedBlocks(node1);
    testMinedBlocks(node2);

    Assert.assertNotEquals(node1.getDB().getBlockHashAtHeight(2), node2.getDB().getBlockHashAtHeight(2));


    node2.getPeerage().connectPeer("localhost", port);

    Thread.sleep(1000);
    Assert.assertEquals(node1.getDB().getBlockHashAtHeight(2), node2.getDB().getBlockHashAtHeight(2));


    miner1.stop();
    miner2.stop();
    Thread.sleep(500);
    node1.stop();
    node2.stop();

  }

  private void testMinedBlocks(SnowBlossomNode node) throws Exception
  {
    waitForMoreBlocks(node, 3);
  }

  private void waitForMoreBlocks(SnowBlossomNode node, int wait_for) throws Exception
  {
    int start = -1;
    if (node.getBlockIngestor().getHead()!=null)
    {
      start = node.getBlockIngestor().getHead().getHeader().getBlockHeight();
    }
    int target = start + wait_for;

    int height = start;
    for (int i = 0; i < 15; i++)
    {
      Thread.sleep(1000);
      height = node.getBlockIngestor().getHead().getHeader().getBlockHeight();
      if (height >= target) return;
    }
    Assert.fail(String.format("Waiting for %d blocks, only got %d", wait_for, height - start));

  }

  private void waitForFunds(SnowBlossomClient client, AddressSpecHash addr, int max_seconds)
    throws Exception
  {
    for(int i=0; i<max_seconds*10; i++)
    {
      Thread.sleep(100);
      if (client.getSpendable(addr).size() > 0) return;

    }

    Assert.fail(String.format("Waiting for funds.  Didn't get any after %d seconds", max_seconds));

  }


  private void testConsolidateFunds(SnowBlossomNode node, SnowBlossomClient client, KeyPair key_pair, AddressSpecHash from_addr) throws Exception
  {
    List<TransactionBridge> funds = client.getSpendable(from_addr);
    
    System.out.println("Funds: " + funds.size());
    Assert.assertTrue(funds.size() > 3);

    KeyPair key_pair_to = KeyUtil.generateECCompressedKey();

    AddressSpec claim = AddressUtil.getSimpleSpecForKey(key_pair_to.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);

    AddressSpecHash to_addr = AddressUtil.getHashForSpec(claim);

    long value = 0;
    LinkedList<TransactionInput> in_list = new LinkedList<>();
    for (TransactionBridge b : funds)
    {
      value += b.value;
      in_list.add(b.in);
    }

    TransactionOutput out = TransactionOutput.newBuilder().setRecipientSpecHash(to_addr.getBytes()).setValue(value).build();

    Transaction tx = TransactionUtil.createTransaction(in_list, ImmutableList.of(out), key_pair);

    Assert.assertTrue(client.submitTransaction(tx));

    waitForMoreBlocks(node, 2);

    List<TransactionBridge> new_funds = client.getSpendable(to_addr);
    Assert.assertEquals(1, new_funds.size());

    TransactionBridge b = new_funds.get(0);
    Assert.assertEquals(value, b.value);

    Assert.assertNotNull(node.getDB());

    TransactionStatus status = TransactionMapUtil.getTxStatus( 
      new ChainHash(tx.getTxHash()), 
      node.getDB(), 
      node.getBlockIngestor().getHead());

    System.out.println(status);
    Assert.assertTrue(status.getConfirmed());

    {
      HistoryList hl = AddressHistoryUtil.getHistory(to_addr, node.getDB(), node.getBlockIngestor().getHead());
      Assert.assertEquals(1, hl.getEntriesCount());
    }
    {
      HistoryList hl = AddressHistoryUtil.getHistory(from_addr, node.getDB(), node.getBlockIngestor().getHead());
      Assert.assertTrue(hl.getEntriesCount()>5);
    }


    System.out.println(tx);

  }

  private File setupSnow() throws Exception
  {
    NetworkParams params = new NetworkParamsRegtest();

    String test_folder_base = test_folder.newFolder().getPath();

    File snow_path = new File(test_folder.newFolder(), "snow");


    for (int i = 0; i < 4; i++)
    {
      SnowFieldInfo info = params.getSnowFieldInfo(i);

      String name = "spoon." + i;

      File field_path = new File(snow_path, name);
      field_path.mkdirs();

      File field = new File(field_path, name + ".snow");

      new SnowFall(field.getPath(), name, info.getLength());
      ByteString root_hash = new SnowMerkle(field_path, name, true).getRootHash();
      Assert.assertEquals(info.getMerkleRootHash(), root_hash);
    }
    return snow_path;

  }

  private SnowBlossomNode startNode(int port) throws Exception
  {

    String test_folder_base = test_folder.newFolder().getPath();

    Map<String, String> config_map = new TreeMap<>();
    config_map.put("db_path", test_folder_base + "/db");
    config_map.put("db_type", "rocksdb");
    config_map.put("service_port", "" + port);
    config_map.put("network", "spoon");
    config_map.put("tx_index", "true");
    config_map.put("addr_index", "true");

    return new SnowBlossomNode(new ConfigMem(config_map));

  }

  private MrPlow startMrPlow(int node_port, AddressSpecHash pool_addr) throws Exception
  {
    String plow_db_path = test_folder.newFolder().getPath();
    Map<String, String> config_map = new TreeMap<>();
    config_map.put("node_host", "localhost");
    config_map.put("node_port", "" + node_port);
    config_map.put("db_type", "rocksdb");
    config_map.put("db_type", "atomic_file");
    config_map.put("db_path", plow_db_path +"/plowdb");
    config_map.put("pool_fee", "0.01");
    config_map.put("pool_address", pool_addr.toAddressString(new NetworkParamsRegtest()));
    config_map.put("mining_pool_port", "" +(node_port+1));
    config_map.put("network", "spoon");
    config_map.put("min_diff", "11");

    return new MrPlow(new ConfigMem(config_map));


  }

  private SnowBlossomMiner startMiner(int port, AddressSpecHash mine_to, File snow_path) throws Exception
  {
    Map<String, String> config_map = new TreeMap<>();
    config_map.put("node_host", "localhost");
    config_map.put("node_port", "" + port);
    config_map.put("threads", "1");
    config_map.put("mine_to_address", mine_to.toAddressString(new NetworkParamsRegtest()));
    config_map.put("snow_path", snow_path.getPath());
    config_map.put("network", "spoon");
    if (port % 2 == 1)
    {
      config_map.put("memfield", "true");
    }

    return new SnowBlossomMiner(new ConfigMem(config_map));

  }

  private PoolMiner startPoolMiner(int port, AddressSpecHash mine_to, File snow_path) throws Exception
  {

    
    String addr = mine_to.toAddressString(new NetworkParamsRegtest());
    System.out.println("Starting miner with " + addr);

    Map<String, String> config_map = new TreeMap<>();
    config_map.put("pool_host", "localhost");
    config_map.put("pool_port", "" + port);
    config_map.put("threads", "1");
    config_map.put("mine_to_address", addr);
    config_map.put("snow_path", snow_path.getPath());
    config_map.put("network", "spoon");
    if (port % 2 == 1)
    {
      config_map.put("memfield", "true");
    }

    return new PoolMiner(new ConfigMem(config_map));

  }

  private SnowBlossomClient startClientWithWallet(int port) throws Exception
  {

    String wallet_path = test_folder.newFolder().getPath();

    Map<String, String> config_map = new TreeMap<>();
    config_map.put("node_uri", "grpc://localhost:" + port);
    config_map.put("network", "spoon");
    config_map.put("wallet_path", wallet_path);

    return new SnowBlossomClient(new ConfigMem(config_map));
  }

 

  private SnowBlossomClient startClient(int port) throws Exception
  {

    Map<String, String> config_map = new TreeMap<>();
    config_map.put("node_uri", "grpc://localhost:" + port);
    config_map.put("network", "spoon");

    return new SnowBlossomClient(new ConfigMem(config_map));
  }

  public static WalletDatabase genWallet()
  {
    TreeMap<String,String> config_map = new TreeMap<>();
    config_map.put("key_count", "20");
    WalletDatabase db = WalletUtil.makeNewDatabase(new ConfigMem(config_map), new NetworkParamsRegtest());
    return db;
  }


}
