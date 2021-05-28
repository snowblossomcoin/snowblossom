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

    String trust_folder_base = test_folder.newFolder().getPath();


    SnowBlossomNode node0 = startNode(0, "regshard", ImmutableMap.of("shards","0", "trustnet_key_path", trust_folder_base));

    AddressSpecHash trust_addr = node0.getTrustnetAddress();
    String trust_str = AddressUtil.getAddressString("node", trust_addr);

    SnowBlossomNode node1 = startNode(0, "regshard",
      ImmutableMap.of("shards","3", "trustnet_key_path", trust_folder_base, "trustnet_signers", trust_str));
    SnowBlossomNode node2 = startNode(0, "regshard",
      ImmutableMap.of("shards","4", "trustnet_key_path", trust_folder_base, "trustnet_signers", trust_str));
    SnowBlossomNode node3 = startNode(0, "regshard",
      ImmutableMap.of("shards","5", "trustnet_key_path", trust_folder_base, "trustnet_signers", trust_str));
    SnowBlossomNode node4 = startNode(0, "regshard",
      ImmutableMap.of("shards","6", "trustnet_key_path", trust_folder_base, "trustnet_signers", trust_str));

    int ports[]=new int[5];
    ports[0] = node0.getServicePorts().get(0);
    ports[1] = node1.getServicePorts().get(0);
    ports[2] = node2.getServicePorts().get(0);
    ports[3] = node3.getServicePorts().get(0);
    ports[4] = node4.getServicePorts().get(0);

    Thread.sleep(100);
    node1.getPeerage().connectPeer("localhost", ports[0]);
    node2.getPeerage().connectPeer("localhost", ports[0]);
    node3.getPeerage().connectPeer("localhost", ports[0]);
    node4.getPeerage().connectPeer("localhost", ports[0]);
    Thread.sleep(3000);

    KeyPair key_pair = KeyUtil.generateECCompressedKey();
    AddressSpec claim = AddressUtil.getSimpleSpecForKey(key_pair.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);
    AddressSpecHash to_addr = AddressUtil.getHashForSpec(claim);

    SnowBlossomMiner miner1 = startMiner(ports[1], to_addr, snow_path, "regshard");
    SnowBlossomMiner miner2 = startMiner(ports[2], to_addr, snow_path, "regshard");
    SnowBlossomMiner miner3 = startMiner(ports[3], to_addr, snow_path, "regshard");
    SnowBlossomMiner miner4 = startMiner(ports[4], to_addr, snow_path, "regshard");

    waitForHeight(node1, 3, 36, 300);
    waitForHeight(node2, 4, 36, 80);
    waitForHeight(node3, 5, 36, 80);
    waitForHeight(node4, 6, 36, 80);

    waitForHeight(node0, 3, 36, 10);
    waitForHeight(node0, 4, 36, 10);
    waitForHeight(node0, 5, 36, 10);
    waitForHeight(node0, 6, 36, 10);

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
