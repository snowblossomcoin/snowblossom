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

public class ShardTestPeering extends SpoonTest
{
  @Test
  public void shardTest() throws Exception
  {
    File snow_path = setupSnow("regshard");

    Random rnd = new Random();
    int port = 20000 + rnd.nextInt(30000);
    SnowBlossomNode node1 = startNode(port, "regshard");
    Thread.sleep(100);
    SnowBlossomNode node2 = startNode(port+1, "regshard");
    Thread.sleep(100);
    node2.getPeerage().connectPeer("localhost", port);

    KeyPair key_pair = KeyUtil.generateECCompressedKey();
    AddressSpec claim = AddressUtil.getSimpleSpecForKey(key_pair.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);
    AddressSpecHash to_addr = AddressUtil.getHashForSpec(claim);

    SnowBlossomMiner miner = startMiner(port, to_addr, snow_path, "regshard");

    waitForHeight(node1, 0, 19);
    waitForHeight(node1, 1, 28);
    waitForHeight(node1, 2, 28);

    waitForHeight(node2, 0, 19);
    waitForHeight(node2, 1, 28);
    waitForHeight(node2, 2, 28);

    miner.stop();
    Thread.sleep(500);
    node1.stop();
    node2.stop();
  }


}
