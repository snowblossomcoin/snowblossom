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
import org.junit.rules.TemporaryFolder;
import snowblossom.client.SnowBlossomClient;
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

  protected void testMinedBlocks(SnowBlossomNode node) throws Exception
  {
    waitForMoreBlocks(node, 3);
  }

  protected void waitForMoreBlocks(SnowBlossomNode node, int wait_for) throws Exception
  {
    waitForMoreBlocks(node, 0, wait_for);
  }
  protected void waitForMoreBlocks(SnowBlossomNode node, int shard_id, int wait_for) throws Exception
  {
    int start = -1;
    if (node.getBlockIngestor(shard_id).getHead()!=null)
    {
      start = node.getBlockIngestor(shard_id).getHead().getHeader().getBlockHeight();
    }
    int target = start + wait_for;

    int height = start;
    for (int i = 0; i < 15; i++)
    {
      Thread.sleep(1000);
      height = node.getBlockIngestor(shard_id).getHead().getHeader().getBlockHeight();
      if (height >= target) return;
    }
    Assert.fail(String.format("Waiting for %d blocks, only got %d", wait_for, height - start));

  }

  protected void waitForHeight(SnowBlossomNode node, int shard_id, int target) throws Exception
  {
    waitForHeight(node, shard_id, target, 15);
  }
  protected void waitForHeight(SnowBlossomNode node, int shard_id, int target, int max_wait) throws Exception
  {

    int height=-1;
    for (int i = 0; i < max_wait; i++)
    {
      Thread.sleep(1000);
      if (node.getBlockIngestor(shard_id) != null)
      if (node.getBlockIngestor(shard_id).getHead() != null)
      {
        height = node.getBlockIngestor(shard_id).getHead().getHeader().getBlockHeight();
        if (height >= target) return;
      }
    }
    Assert.fail(String.format("Waiting for %d blocks, only got %d", target, height));

  }


  protected void waitForShardOpen(SnowBlossomNode node, int shard_id) throws Exception
  {
    for(int i=0; i<15; i++)
    {
      if (node.getActiveShards().contains(shard_id)) return;
      Thread.sleep(1000);
    }
    Assert.fail(String.format("Shard %d did not become active", shard_id));

  }
  protected void waitForShardHead(SnowBlossomNode node, int shard_id) throws Exception
  {
    for(int i=0; i<15; i++)
    {
      if (node.getActiveShards().contains(shard_id))
      if (node.getBlockIngestor(shard_id).getHead() != null)
      {
        return;
      }
      Thread.sleep(1000);
    }
    Assert.fail(String.format("Shard %d did not become active", shard_id));

  }



  protected void waitForFunds(SnowBlossomClient client, AddressSpecHash addr, int max_seconds)
    throws Exception
  {
    for(int i=0; i<max_seconds*10; i++)
    {
      Thread.sleep(100);
      if (client.getSpendable(addr).size() > 0) return;

    }

    Assert.fail(String.format("Waiting for funds.  Didn't get any after %d seconds", max_seconds));

  }


  protected void testConsolidateFunds(SnowBlossomNode node, SnowBlossomClient client, KeyPair key_pair, AddressSpecHash from_addr) throws Exception
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

  protected File setupSnow() throws Exception
  {
    return setupSnow("spoon");
  }
  protected File setupSnow(String network) throws Exception
  {
    TreeMap<String, String> config_map = new TreeMap<>();
    config_map.put("network", network);

    NetworkParams params = NetworkParams.loadFromConfig(new ConfigMem(config_map));

    String test_folder_base = test_folder.newFolder().getPath();

    File snow_path = new File(test_folder.newFolder(), "snow");

    for (int i = 0; i < 4; i++)
    {
      SnowFieldInfo info = params.getSnowFieldInfo(i);

      String name = network + "." + i;

      File field_path = new File(snow_path, name);
      field_path.mkdirs();

      File field = new File(field_path, name + ".snow");

      new SnowFall(field.getPath(), name, info.getLength());
      ByteString root_hash = new SnowMerkle(field_path, name, true).getRootHash();
      Assert.assertEquals(info.getMerkleRootHash(), root_hash);
    }
    return snow_path;

  }

  protected SnowBlossomNode startNode(int port) throws Exception
  {
    return startNode(port, "spoon");
  }
  protected SnowBlossomNode startNode(int port, String network) throws Exception
  {
    return startNode(port, network, null);
  }
  protected SnowBlossomNode startNode(int port, String network, Map<String, String> extra) throws Exception
  {

    String test_folder_base = test_folder.newFolder().getPath();

    Map<String, String> config_map = new TreeMap<>();
    config_map.put("db_path", test_folder_base + "/db");
    config_map.put("db_type", "rocksdb");
    config_map.put("service_port", "" + port);
    config_map.put("network", network);
    config_map.put("tx_index", "true");
    config_map.put("addr_index", "true");

    if (extra!=null)
    {
      config_map.putAll(extra);
    }

    return new SnowBlossomNode(new ConfigMem(config_map));

  }

  protected MrPlow startMrPlow(int node_port, AddressSpecHash pool_addr) throws Exception
  {
    return startMrPlow(node_port, pool_addr, "spoon");
  }
  protected MrPlow startMrPlow(int node_port, AddressSpecHash pool_addr, String network) throws Exception
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
    config_map.put("network", network);
    config_map.put("min_diff", "11");

    return new MrPlow(new ConfigMem(config_map));


  }

  protected SnowBlossomMiner startMiner(int port, AddressSpecHash mine_to, File snow_path) throws Exception
  {
    return startMiner(port, mine_to, snow_path, "spoon");
  }
  protected SnowBlossomMiner startMiner(int port, AddressSpecHash mine_to, File snow_path, String network) throws Exception
  {
    Map<String, String> config_map = new TreeMap<>();
    config_map.put("node_host", "localhost");
    config_map.put("node_port", "" + port);
    config_map.put("threads", "1");
    config_map.put("snow_path", snow_path.getPath());
    config_map.put("network", network);
    NetworkParams params = NetworkParams.loadFromConfig(new ConfigMem(config_map));
    config_map.put("mine_to_address", mine_to.toAddressString(params));
    config_map.put("rate_limit","100000.0"); 
    if (port % 2 == 1)
    {
      config_map.put("memfield", "true");
    }

    return new SnowBlossomMiner(new ConfigMem(config_map));

  }

  protected PoolMiner startPoolMiner(int port, AddressSpecHash mine_to, File snow_path) throws Exception
  {
    return startPoolMiner(port, mine_to, snow_path, "spoon");
  }
  protected PoolMiner startPoolMiner(int port, AddressSpecHash mine_to, File snow_path, String network) throws Exception
  {
    String addr = mine_to.toAddressString(new NetworkParamsRegtest());
    System.out.println("Starting miner with " + addr);

    Map<String, String> config_map = new TreeMap<>();
    config_map.put("pool_host", "localhost");
    config_map.put("pool_port", "" + port);
    config_map.put("threads", "1");
    config_map.put("mine_to_address", addr);
    config_map.put("snow_path", snow_path.getPath());
    config_map.put("network", network);
    if (port % 2 == 1)
    {
      config_map.put("memfield", "true");
    }

    return new PoolMiner(new ConfigMem(config_map));

  }

  protected SnowBlossomClient startClientWithWallet(int port) throws Exception
  {

    String wallet_path = test_folder.newFolder().getPath();

    Map<String, String> config_map = new TreeMap<>();
    config_map.put("node_uri", "grpc://localhost:" + port);
    config_map.put("network", "spoon");
    config_map.put("wallet_path", wallet_path);

    return new SnowBlossomClient(new ConfigMem(config_map));
  }

 

  protected SnowBlossomClient startClient(int port) throws Exception
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
