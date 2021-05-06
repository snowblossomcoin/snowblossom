package systemtests.test;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.security.KeyPair;
import java.util.*;
import org.junit.Test;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.KeyUtil;
import snowblossom.lib.SignatureUtil;
import snowblossom.miner.SnowBlossomMiner;
import snowblossom.node.SnowBlossomNode;
import snowblossom.proto.*;
import snowblossom.util.proto.*;

public class ShardTestFullJambo extends SpoonTest
{
  /**
   * Run four nodes, each with some sub sets, with no overlap.
   * They have to use trust network to get anywhere.
   * node-0 has no miner and views entire network. 
   * using it as an easy way to see that network status
   * and as a p2p networking gateway
   */
  @Test
  public void shardTest() throws Exception
  {
    File snow_path = setupSnow("regshard");

    Random rnd = new Random();
    int port = 20000 + rnd.nextInt(30000);

    String trust_folder_base = test_folder.newFolder().getPath();

    SnowBlossomNode node0 = startNode(port-1, "regshard", ImmutableMap.of("shards","0", "trustnet_key_path", trust_folder_base));
    Thread.sleep(10000);
    AddressSpecHash trust_addr = node0.getTrustnetAddress();
    String trust_str = AddressUtil.getAddressString("node", trust_addr);

    SnowBlossomNode node1 = startNode(port+0, "regshard", 
      ImmutableMap.of("shards","3", "trustnet_key_path", trust_folder_base, "trustnet_signers", trust_str));
    SnowBlossomNode node2 = startNode(port+1, "regshard",
      ImmutableMap.of("shards","4", "trustnet_key_path", trust_folder_base, "trustnet_signers", trust_str));
    SnowBlossomNode node3 = startNode(port+2, "regshard",
      ImmutableMap.of("shards","5", "trustnet_key_path", trust_folder_base, "trustnet_signers", trust_str));
    SnowBlossomNode node4 = startNode(port+3, "regshard",
      ImmutableMap.of("shards","6", "trustnet_key_path", trust_folder_base, "trustnet_signers", trust_str));
    Thread.sleep(100);
    node1.getPeerage().connectPeer("localhost", port-1);
    node2.getPeerage().connectPeer("localhost", port-1);
    node3.getPeerage().connectPeer("localhost", port-1);
    node4.getPeerage().connectPeer("localhost", port-1);
    Thread.sleep(1000);

    KeyPair key_pair = KeyUtil.generateECCompressedKey();
    AddressSpec claim = AddressUtil.getSimpleSpecForKey(key_pair.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);
    AddressSpecHash to_addr = AddressUtil.getHashForSpec(claim);

    SnowBlossomMiner miner1 = startMiner(port+0, to_addr, snow_path, "regshard");
    SnowBlossomMiner miner2 = startMiner(port+1, to_addr, snow_path, "regshard");
    SnowBlossomMiner miner3 = startMiner(port+2, to_addr, snow_path, "regshard");
    SnowBlossomMiner miner4 = startMiner(port+3, to_addr, snow_path, "regshard");

    waitForHeight(node0, 3, 36, 180);
    waitForHeight(node0, 4, 36, 180);
    waitForHeight(node0, 5, 36, 180);
    waitForHeight(node0, 6, 36, 180);
    
    miner1.stop();
    miner2.stop();
    miner3.stop();
    miner4.stop();
    Thread.sleep(500);
    node0.stop();
    node1.stop();
    node2.stop();
    node3.stop();
    node4.stop();
  }


}