package systemtests.test;

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

public class ShardTestBasic extends SpoonTest
{
  @Test
  public void shardTest() throws Exception
  {
    File snow_path = setupSnow("regshard");

    SnowBlossomNode node = startNode(0, "regshard");
    int port = node.getServicePorts().get(0);
    Thread.sleep(100);

    KeyPair key_pair = KeyUtil.generateECCompressedKey();
    AddressSpec claim = AddressUtil.getSimpleSpecForKey(key_pair.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);
    AddressSpecHash to_addr = AddressUtil.getHashForSpec(claim);

    SnowBlossomMiner miner = startMiner(port, to_addr, snow_path, "regshard");
    SnowBlossomMiner miner2 = startMiner(port, to_addr, snow_path, "regshard");


    waitForHeight(node, 0, 19,75);
    waitForHeight(node, 1, 28,35);
    waitForHeight(node, 2, 28,25);
    waitForHeight(node, 3, 38,35);
    waitForHeight(node, 4, 38,25);
    waitForHeight(node, 5, 38,25);
    waitForHeight(node, 6, 38,25);

    System.out.println(node.getBlockIngestor(0).getHead());
    System.out.println(node.getBlockIngestor(2).getHead());

    miner.stop();
    miner2.stop();
    Thread.sleep(500);
    node.stop();
  }


}
