package snowblossom;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import duckutil.ConfigMem;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import snowblossom.client.SnowBlossomClient;
import snowblossom.miner.SnowBlossomMiner;
import snowblossom.proto.*;
import snowblossomlib.AddressSpecHash;
import snowblossomlib.AddressUtil;
import snowblossomlib.KeyUtil;
import snowblossomlib.NetworkParams;
import snowblossomlib.NetworkParamsRegtest;
import snowblossomlib.SignatureUtil;
import snowblossomlib.SnowBlossomNode;
import snowblossomlib.SnowFall;
import snowblossomlib.SnowFieldInfo;
import snowblossomlib.SnowMerkle;
import snowblossomlib.TransactionBridge;
import snowblossomlib.TransactionUtil;

import java.io.File;
import java.security.KeyPair;
import java.util.*;


public class SpoonTest
{
  @Rule
  public TemporaryFolder test_folder = new TemporaryFolder();

  /**
   * More of a giant orbital space platform full of weasels
   * than a unit test
   */
  @Test
  public void spoonTest()
    throws Exception
  {
    File snow_path = setupSnow();

    Random rnd = new Random();
    int port = 20000 + rnd.nextInt(30000);
    snowblossomlib.SnowBlossomNode node = startNode(port);
    Thread.sleep(100);

    KeyPair key_pair = snowblossomlib.KeyUtil.generateECCompressedKey();

    AddressSpec claim = snowblossomlib.AddressUtil.getSimpleSpecForKey(key_pair.getPublic(), snowblossomlib.SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);

    snowblossomlib.AddressSpecHash to_addr = snowblossomlib.AddressUtil.getHashForSpec(claim);

    SnowBlossomMiner miner = startMiner(port, to_addr, snow_path);

    testMinedBlocks(node);

    SnowBlossomClient client = startClient(port);
    testConsolidateFunds(node, client, key_pair, to_addr);


    snowblossomlib.SnowBlossomNode node2 = startNode(port+1);
    node2.getPeerage().connectPeer("localhost", port);
    testMinedBlocks(node2);


		miner.stop();
    Thread.sleep(500);
    node.stop();
    node2.stop();
  }

  @Test
  public void networkReconsileTest()
    throws Exception
  {
    File snow_path = setupSnow();

    Random rnd = new Random();
    int port = 20000 + rnd.nextInt(30000);
    snowblossomlib.SnowBlossomNode node1 = startNode(port);
    snowblossomlib.SnowBlossomNode node2 = startNode(port+1);
    Thread.sleep(100);

    KeyPair key_pair = snowblossomlib.KeyUtil.generateECCompressedKey();

    AddressSpec claim = snowblossomlib.AddressUtil.getSimpleSpecForKey(key_pair.getPublic(), snowblossomlib.SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);

    snowblossomlib.AddressSpecHash to_addr = snowblossomlib.AddressUtil.getHashForSpec(claim);

    SnowBlossomMiner miner1 = startMiner(port, to_addr, snow_path);
    SnowBlossomMiner miner2 = startMiner(port+1, to_addr, snow_path);

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

  private void testMinedBlocks(snowblossomlib.SnowBlossomNode node)
    throws Exception
  {
    for(int i=0; i<15; i++)
    {
      Thread.sleep(1000);

      BlockSummary summary = node.getBlockIngestor().getHead();
      if (summary != null)
      {

        int height = summary.getHeader().getBlockHeight();
        if (height > 2) return;
      }
    }
    Assert.fail("Does not seem to be making blocks");
  }

  private void waitForMoreBlocks(snowblossomlib.SnowBlossomNode node, int wait_for)
    throws Exception
  {
    int start = node.getBlockIngestor().getHead().getHeader().getBlockHeight();
    int target = start + wait_for;

    int height=start;
    for(int i=0; i<15; i++)
    {
      Thread.sleep(1000);
      height = node.getBlockIngestor().getHead().getHeader().getBlockHeight();
      if (height >= target) return;
    }
    Assert.fail(String.format("Waiting for %d blocks, only got %d", wait_for, height - start));

  }

  private void testConsolidateFunds(snowblossomlib.SnowBlossomNode node, SnowBlossomClient client, KeyPair key_pair, snowblossomlib.AddressSpecHash from_addr)
    throws Exception
  {
    List<snowblossomlib.TransactionBridge> funds = client.getSpendable(from_addr);

    Assert.assertTrue(funds.size() > 3);

    KeyPair key_pair_to = KeyUtil.generateECCompressedKey();

    AddressSpec claim = snowblossomlib.AddressUtil.getSimpleSpecForKey(key_pair_to.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);

    snowblossomlib.AddressSpecHash to_addr = AddressUtil.getHashForSpec(claim);

    long value = 0;
    LinkedList<TransactionInput> in_list = new LinkedList<>();
    for(snowblossomlib.TransactionBridge b : funds)
    {
      value += b.value;
      in_list.add(b.in);
    }

    TransactionOutput out = TransactionOutput.newBuilder()
      .setRecipientSpecHash(to_addr.getBytes())
      .setValue(value)
      .build();

    Transaction tx = TransactionUtil.createTransaction(in_list, ImmutableList.of(out), key_pair);

    Assert.assertTrue(client.submitTransaction(tx));

    waitForMoreBlocks(node, 2);

    List<snowblossomlib.TransactionBridge> new_funds = client.getSpendable(to_addr);
    Assert.assertEquals(1, new_funds.size());

    TransactionBridge b = new_funds.get(0);
    Assert.assertEquals(value, b.value);

    System.out.println(tx);
 

  }

  private File setupSnow()
    throws Exception
  {
    NetworkParams params = new snowblossomlib.NetworkParamsRegtest();

    String test_folder_base = test_folder.newFolder().getPath();

    File snow_path = new File( test_folder.newFolder(), "snow");
    

    for(int i=0; i<4; i++)
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

  private snowblossomlib.SnowBlossomNode startNode(int port)
    throws Exception
  {
    
    String test_folder_base = test_folder.newFolder().getPath();

    Map<String, String> config_map = new TreeMap<>();
    config_map.put("db_path", test_folder_base + "/db");
    config_map.put("db_type", "rocksdb");
    config_map.put("service_port", "" + port);
    config_map.put("network","spoon");
    
    return new SnowBlossomNode(new ConfigMem(config_map));

  }

  private SnowBlossomMiner startMiner(int port, AddressSpecHash mine_to, File snow_path)
    throws Exception
  {
    Map<String, String> config_map = new TreeMap<>();
    config_map.put("node_host", "localhost");
    config_map.put("node_port", "" + port);
    config_map.put("threads", "1");
    config_map.put("mine_to_address", mine_to.toAddressString(new NetworkParamsRegtest()));
    config_map.put("snow_path", snow_path.getPath());
    config_map.put("network","spoon");
    if (port % 2 == 1)
    {
      config_map.put("memfield","true");
    }

    return new SnowBlossomMiner(new ConfigMem(config_map));

  }


  private SnowBlossomClient startClient(int port)
    throws Exception
  {
    Map<String, String> config_map = new TreeMap<>();
    config_map.put("node_host", "localhost");
    config_map.put("node_port", "" + port);
    config_map.put("network","spoon");

    return new SnowBlossomClient(new ConfigMem(config_map));

  }



}
