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

public class ShardTestDuelPool extends SpoonTest
{

  SnowBlossomNode node_a_seed;
  SnowBlossomNode node_a_1;
  SnowBlossomNode node_a_2;
  SnowBlossomNode node_b_seed;
  SnowBlossomNode node_b_1;
  SnowBlossomNode node_b_2;

  /**
   * Run two groups of nodes that don't trust each other
   */
  @Test
  public void shardTest() throws Exception
  {
    File snow_path = setupSnow("regshard");

    String trust_folder_base_a = test_folder.newFolder().getPath();
    String trust_folder_base_b = test_folder.newFolder().getPath();

    node_a_seed = startNode(0, "regshard",
      ImmutableMap.of("shards","0", "trustnet_key_path", trust_folder_base_a));

    node_b_seed = startNode(0, "regshard",
      ImmutableMap.of("shards","0", "trustnet_key_path", trust_folder_base_b));

    AddressSpecHash trust_addr_a = node_a_seed.getTrustnetAddress();
    String trust_str_a = AddressUtil.getAddressString("node", trust_addr_a);

    AddressSpecHash trust_addr_b = node_b_seed.getTrustnetAddress();
    String trust_str_b = AddressUtil.getAddressString("node", trust_addr_b);

    node_a_1 = startNode(0, "regshard",
      ImmutableMap.of("shards","1", "trustnet_key_path", trust_folder_base_a, "trustnet_signers", trust_str_a));
    node_a_2 = startNode(0, "regshard",
      ImmutableMap.of("shards","2", "trustnet_key_path", trust_folder_base_a, "trustnet_signers", trust_str_a));

    node_b_1 = startNode(0, "regshard",
      ImmutableMap.of("shards","1", "trustnet_key_path", trust_folder_base_b, "trustnet_signers", trust_str_b));
    node_b_2 = startNode(0, "regshard",
      ImmutableMap.of("shards","2", "trustnet_key_path", trust_folder_base_b, "trustnet_signers", trust_str_b));

    int seed_port = node_a_seed.getServicePorts().get(0);


    Thread.sleep(100);
    node_a_1.getPeerage().connectPeer("localhost", seed_port);
    node_a_2.getPeerage().connectPeer("localhost", seed_port);
    node_b_1.getPeerage().connectPeer("localhost", seed_port);
    node_b_2.getPeerage().connectPeer("localhost", seed_port);
    node_b_seed.getPeerage().connectPeer("localhost", seed_port);
    Thread.sleep(3000);

    KeyPair key_pair_a = KeyUtil.generateECCompressedKey();
    AddressSpec claim_a = AddressUtil.getSimpleSpecForKey(key_pair_a.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);
    AddressSpecHash to_addr_a = AddressUtil.getHashForSpec(claim_a);

    KeyPair key_pair_b = KeyUtil.generateECCompressedKey();
    AddressSpec claim_b = AddressUtil.getSimpleSpecForKey(key_pair_b.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);
    AddressSpecHash to_addr_b = AddressUtil.getHashForSpec(claim_b);

    SnowBlossomMiner miner1 = startMiner(node_a_1.getServicePorts().get(0), to_addr_a, snow_path, "regshard");
    SnowBlossomMiner miner2 = startMiner(node_a_2.getServicePorts().get(0), to_addr_a, snow_path, "regshard");
    SnowBlossomMiner miner3 = startMiner(node_b_1.getServicePorts().get(0), to_addr_b, snow_path, "regshard");
    SnowBlossomMiner miner4 = startMiner(node_b_2.getServicePorts().get(0), to_addr_b, snow_path, "regshard");

    waitForHeight(node_b_seed, 3, 36, 100);
    waitForHeight(node_b_seed, 4, 36, 80);
    waitForHeight(node_b_seed, 5, 36, 80);
    waitForHeight(node_b_seed, 6, 36, 80);

    //waitForHeight(node0, 3, 36, 100);
    //waitForHeight(node0, 4, 36, 20);
    //waitForHeight(node0, 5, 36, 20);
    //waitForHeight(node0, 6, 36, 20);

    miner1.stop();
    miner2.stop();
    miner3.stop();
    miner4.stop();
    Thread.sleep(500);
    node_a_seed.stop();
    node_b_seed.stop();
    node_a_1.stop();
    node_a_2.stop();
    node_b_1.stop();
    node_b_2.stop();
  }

  @Override
  public void preFailReport()
    throws Exception
  {
    printNodeShardStatus(node_a_seed, "node_a_seed");
    printNodeShardStatus(node_b_seed, "node_b_seed");
    printNodeShardStatus(node_a_1, "node_a_1");
    printNodeShardStatus(node_a_2, "node_a_2");
    printNodeShardStatus(node_b_1, "node_b_1");
    printNodeShardStatus(node_b_2, "node_b_2");

  }

}
