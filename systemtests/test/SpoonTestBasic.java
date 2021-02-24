package systemtests.test;

import java.io.File;
import java.security.KeyPair;
import java.util.*;
import org.junit.Test;
import snowblossom.client.SnowBlossomClient;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.KeyUtil;
import snowblossom.lib.SignatureUtil;
import snowblossom.miner.SnowBlossomMiner;
import snowblossom.node.SnowBlossomNode;
import snowblossom.proto.*;
import snowblossom.util.proto.*;

public class SpoonTestBasic extends SpoonTest
{
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


}
