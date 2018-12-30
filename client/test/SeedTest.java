package client.test;

import org.junit.Assert;
import org.junit.Test;
import snowblossom.client.SeedUtil;

import java.util.List;
import com.google.protobuf.ByteString;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.ChildNumber;
import com.google.common.collect.ImmutableList;
import snowblossom.proto.WalletKeyPair;
import snowblossom.lib.*;
import snowblossom.proto.*;
import java.util.Random;
import java.util.logging.Logger;

public class SeedTest
{
	private static final Logger logger = Logger.getLogger("SeedTest");

  @Test
  public void testMnemonicCode()
  {
    SeedUtil.getMCode();
  }
 
  @Test
  public void testVectors() throws Exception
  {
    testVector(
      "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
      "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04",
      "TREZOR",
      "xprv9z3rKW6EbJfnxwVmsXBRWABiT5Zfh37GsqTXrcdaMssUntP2wumTtqKxSkZsytaxQZknwAhb3U8UR5cc3cxoMxdo4871tPPCTmeqckJyrWL");

    testVector(
      "hamster diagram private dutch cause delay private meat slide toddler razor book happy fancy gospel tennis maple dilemma loan word shrug inflict delay length",
      "64c87cde7e12ecf6704ab95bb1408bef047c22db4cc7491c4271d170a1b213d20b385bc1588d9c7b38f1b39d415665b8a9030c9ec653d75e65f847d8fc1fc440",
      "TREZOR",
      "xprv9zHXv6jsJqsCCWh1djuAv2qEagWaTp8NLX7xHiEg1qzLjwHc49ECpVzqqCtA5H1fxn1YvhqJ1m9AyzJN4QsceuxPcRUNVH17NjQgZJaH7nz");

    testVector(
      "void come effort suffer camp survey warrior heavy shoot primary clutch crush open amazing screen patrol group space point ten exist slush involve unfold",
      "01f5bced59dec48e362f2c45b5de68b9fd6c92c6634f44d6d40aab69056506f0e35524a518034ddc1192e1dacd32c1ed3eaa3c3b131c88ed8e7e54c49a5d0998",
      "TREZOR",
      "xprv9yar4XDxTN3AkM6t5UTtQjnc5ATco97N59aotjXRPwB81mAbLb1gd7te6vsBr244jLzMuzB9xWFxyrWv5h1Q67xYfYuqFcXFHqzpbUKp9PW");

    testVector(
      "wall share bronze security indoor bottom diet dirt work century that virus",
      "0e6746e73b79b00c056bb50a3eff29de6ec230022e65f6ba78662b7b8e0bc201300daee0b8e7fffe3fa66c03f87dd2c691c14f4001184016dbc2ea243ef15681",
      "",
      "xprv9yNTk52rJjHT9UkFnaBZTC2ZhY4iEQkg74d3Wq9xTCanZvXFtdfLjtyeSh21xj6qMrfPNGowN2jKSByxzfRmAkLhuhP4DCSJVoMBxc7DvuP");
  }

  private void testVector(String seed, String data, String pw, String xprv)
    throws Exception
  {
    ByteString expected = HexUtil.hexStringToBytes(data);

    ByteString found = SeedUtil.decodeSeed(seed, pw);

    Assert.assertEquals( HexUtil.getHexString(expected), HexUtil.getHexString(found));

    DeterministicKey dk = HDKeyDerivation.createMasterPrivateKey(found.toByteArray());
    DeterministicHierarchy dh = new DeterministicHierarchy(dk);

    DeterministicKey dk_acct = dh.get( ImmutableList.of(
      new ChildNumber(44,true),
      new ChildNumber(0,true),
      new ChildNumber(0,true)),
      true, true);

    Assert.assertEquals(
      xprv,
      dk_acct.serializePrivB58(org.bitcoinj.params.MainNetParams.get()));
  }

  @Test
  public void testGenerate() throws Exception
  {
    testGen(12);
    testGen(18);
    testGen(24);
  }

  private void testGen(int words)
    throws Exception
  {
    String seed = SeedUtil.generateSeed(words);

    List<String> lst = SeedUtil.getWordsFromSeed(seed);

    Assert.assertEquals(words, lst.size());

    ByteString seed_data = SeedUtil.decodeSeed(seed, "");
    System.out.println("Seed: " + seed + " " + HexUtil.getHexString(seed_data));

    DeterministicKey dk = HDKeyDerivation.createMasterPrivateKey(seed_data.toByteArray());
    DeterministicHierarchy dh = new DeterministicHierarchy(dk);

    System.out.println("Seed dk: " + dk.toString());

    System.out.println("Seed ser: " + dk.serializePrivB58(org.bitcoinj.params.MainNetParams.get()));

    DeterministicKey dk_acct = dh.get( ImmutableList.of(
      new ChildNumber(44,true),
      new ChildNumber(0,true),
      new ChildNumber(0,true)),
      true, true);
    System.out.println("Seed ser: " + dk_acct.serializePubB58(org.bitcoinj.params.MainNetParams.get()));

    System.out.println("Seed acct: " + dk_acct.toString());
    System.out.println("Seed acct xprv: " + dk_acct.serializePrivB58(org.bitcoinj.params.MainNetParams.get()));

		for(int c=0; c<2; c++)
		for(int i=0; i<100; i++)
		{
    	WalletKeyPair wkp = SeedUtil.getKey( new NetworkParamsTestnet(), seed, "", 0, c, i);
      System.out.println("Seed wkp: " + HexUtil.getHexString(wkp.getSeedId()) + " " + wkp.getHdPath());
      Assert.assertTrue(wkp.getHdPath().startsWith("M/44H/2339H/0H/"));
      Assert.assertTrue(wkp.getSeedId().size()==20);

			testKeyPair(wkp, "hd");
		}

  }

  private static void testKeyPair(WalletKeyPair wkp, String name)
    throws Exception
  {

		WalletKeyPair ref = KeyUtil.generateWalletStandardECKey();
		logger.info(String.format("Reference key pub %d priv %d", ref.getPublicKey().size(), ref.getPrivateKey().size()));
		logger.info(String.format("testwkp key pub %d priv %d", wkp.getPublicKey().size(), wkp.getPrivateKey().size()));
		
    Random rnd = new Random();
    byte[] b = new byte[Globals.BLOCKCHAIN_HASH_LEN];
    rnd.nextBytes(b);

    ChainHash hash = new ChainHash(b);

    ByteString sig = SignatureUtil.sign(wkp, hash);
    SigSpec sig_spec = SigSpec.newBuilder()
      .setSignatureType(wkp.getSignatureType())
      .setPublicKey(wkp.getPublicKey())
      .build();

    logger.info(String.format("Key report %s Pub size: %d, sig %d", name, wkp.getPublicKey().size(), sig.size()));

    Assert.assertTrue(SignatureUtil.checkSignature(sig_spec, hash.getBytes(), sig));

  }


}

