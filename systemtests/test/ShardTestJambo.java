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

public class ShardTestJambo extends SpoonTest
{
  /**
   * Run four nodes, each with some sub sets, but with overlap
   * so that blocks can be linked.
   */
  @Test
  public void shardTest() throws Exception
  {
    File snow_path = setupSnow("regshard");

    Random rnd = new Random();
    int port = 20000 + rnd.nextInt(30000);

    SnowBlossomNode node1 = startNode(port, "regshard", ImmutableMap.of("shards","3,4"));
    SnowBlossomNode node2 = startNode(port+1, "regshard", ImmutableMap.of("shards","4,5"));
    SnowBlossomNode node3 = startNode(port+2, "regshard", ImmutableMap.of("shards","5,6"));
    SnowBlossomNode node4 = startNode(port+3, "regshard", ImmutableMap.of("shards","6,3"));
    Thread.sleep(100);
    node1.getPeerage().connectPeer("localhost", port+1);
    node2.getPeerage().connectPeer("localhost", port+2);
    node3.getPeerage().connectPeer("localhost", port+3);
    node4.getPeerage().connectPeer("localhost", port+0);
    Thread.sleep(15000);

    KeyPair key_pair = KeyUtil.generateECCompressedKey();
    AddressSpec claim = AddressUtil.getSimpleSpecForKey(key_pair.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);
    AddressSpecHash to_addr = AddressUtil.getHashForSpec(claim);

    SnowBlossomMiner miner1 = startMiner(port, to_addr, snow_path, "regshard");
    SnowBlossomMiner miner2 = startMiner(port+1, to_addr, snow_path, "regshard");
    SnowBlossomMiner miner3 = startMiner(port+2, to_addr, snow_path, "regshard");
    SnowBlossomMiner miner4 = startMiner(port+3, to_addr, snow_path, "regshard");

    Thread.sleep(45000);

    waitForHeight(node1, 3, 31);
    waitForHeight(node2, 4, 31);
    waitForHeight(node3, 5, 31);
    waitForHeight(node4, 6, 31);
    
    miner1.stop();
    miner2.stop();
    miner3.stop();
    miner4.stop();
    Thread.sleep(500);
    node1.stop();
    node2.stop();
    node3.stop();
    node4.stop();
  }


}
