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
import snowblossom.miner.PoolMiner;
import snowblossom.miner.plow.MrPlow;
import snowblossom.node.SnowBlossomNode;
import snowblossom.proto.*;
import snowblossom.util.proto.*;

public class SpoonTestPool extends SpoonTest
{
  @Test
  public void spoonPoolTest() throws Exception
  {
    File snow_path = setupSnow();

    Random rnd = new Random();
    int port = 20000 + rnd.nextInt(30000);
    SnowBlossomNode node = startNode(port);
    Thread.sleep(100);

    KeyPair key_pair = KeyUtil.generateECCompressedKey();
    AddressSpec claim = AddressUtil.getSimpleSpecForKey(key_pair.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);
    AddressSpecHash to_addr = AddressUtil.getHashForSpec(claim);

    KeyPair key_pair2 = KeyUtil.generateECCompressedKey();
    AddressSpec claim2 = AddressUtil.getSimpleSpecForKey(key_pair2.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);
    AddressSpecHash to_addr2 = AddressUtil.getHashForSpec(claim2);

    KeyPair key_pair3 = KeyUtil.generateECCompressedKey();
    AddressSpec claim3 = AddressUtil.getSimpleSpecForKey(key_pair3.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);
    AddressSpecHash to_addr3 = AddressUtil.getHashForSpec(claim3);

    SnowBlossomClient client = startClient(port);

    MrPlow plow = startMrPlow(port, to_addr2);

    PoolMiner miner = startPoolMiner(port+1, to_addr, snow_path);
    Thread.sleep(12000);

    waitForMoreBlocks(node, 10);

    System.out.println("ShareMap: " + plow.getShareManager().getShareMap());
    System.out.println("ShareMap pay: " + plow.getShareManager().getPayRatios());

    // Pool getting paid
    waitForFunds(client, to_addr2, 10);

    // Miner getting paid
    waitForFunds(client, to_addr, 30);

    PoolMiner miner2 = startPoolMiner(port+1, to_addr3, snow_path);

    // Second miner getting paid
    waitForFunds(client, to_addr3, 30);
    
    miner.stop();
    miner2.stop();
    Thread.sleep(500);
    plow.stop();
    node.stop();

  }

}
