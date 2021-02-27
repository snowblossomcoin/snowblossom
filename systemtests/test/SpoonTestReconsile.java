package systemtests.test;

import java.io.File;
import java.security.KeyPair;
import java.util.*;
import org.junit.Assert;
import org.junit.Test;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.KeyUtil;
import snowblossom.lib.SignatureUtil;
import snowblossom.miner.SnowBlossomMiner;
import snowblossom.node.SnowBlossomNode;
import snowblossom.proto.*;
import snowblossom.util.proto.*;

public class SpoonTestReconsile extends SpoonTest
{
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

    // Two nodes are on different blocks, they are not connected
    Assert.assertNotEquals(node1.getDB().getBlockHashAtHeight(2), node2.getDB().getBlockHashAtHeight(2));


    // Connect them
    node2.getPeerage().connectPeer("localhost", port);

    Thread.sleep(2000);

    miner1.stop();
    miner2.stop();
    Thread.sleep(500);
    //Then they should be the same
    Assert.assertEquals(node1.getDB().getBlockHashAtHeight(2), node2.getDB().getBlockHashAtHeight(2));


    Thread.sleep(500);
    node1.stop();
    node2.stop();

  }

}
