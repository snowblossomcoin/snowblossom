package lib.test;

import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import snowblossom.lib.ChainHash;
import snowblossom.lib.DigestUtil;
import snowblossom.lib.Globals;

import java.security.MessageDigest;
import java.util.Random;

public class DigestUtilTest
{
  private Random rnd = new Random();

  @BeforeClass
  public static void loadProvider()
  {
    Globals.addCryptoProvider();
  }


  @Test
  public void testMerkleRootSingle()
  {
    ChainHash a = getRandomHash();
    
    ChainHash result = DigestUtil.getMerkleRootForTxList(ImmutableList.of(a));

    Assert.assertEquals(a, result);
  }

  @Test
  public void testMerkleRoot2()
  {
    ChainHash a = getRandomHash();
    ChainHash b = getRandomHash();
    
    ChainHash result = DigestUtil.getMerkleRootForTxList(ImmutableList.of(a, b));

    ChainHash ab = treeHash(a, b);

    Assert.assertEquals(ab, result);
  }

  @Test
  public void testMerkleRoot3()
  {
    ChainHash a = getRandomHash();
    ChainHash b = getRandomHash();
    ChainHash c = getRandomHash();
    
    ChainHash result = DigestUtil.getMerkleRootForTxList(ImmutableList.of(a, b, c));

    ChainHash ab = treeHash(a, b);
    ChainHash ab_c = treeHash(ab, c);

    Assert.assertEquals(ab_c, result);
  }

  @Test
  public void testMerkleRoot8()
  {
    ChainHash a = getRandomHash();
    ChainHash b = getRandomHash();
    ChainHash c = getRandomHash();
    ChainHash d = getRandomHash();
    ChainHash e = getRandomHash();
    ChainHash f = getRandomHash();
    ChainHash g = getRandomHash();
    ChainHash h = getRandomHash();
    
    ChainHash result = DigestUtil.getMerkleRootForTxList(ImmutableList.of(a, b, c, d, e, f, g, h));

    ChainHash ab = treeHash(a, b);
    ChainHash cd = treeHash(c, d);
    ChainHash ef = treeHash(e, f);
    ChainHash gh = treeHash(g, h);

    ChainHash abcd = treeHash(ab, cd);
    ChainHash efgh = treeHash(ef, gh);


    ChainHash abcdefgh = treeHash(abcd, efgh);

    Assert.assertEquals(abcdefgh, result);
  }
  @Test
  public void testMerkleRoot9()
  {
    ChainHash a = getRandomHash();
    ChainHash b = getRandomHash();
    ChainHash c = getRandomHash();
    ChainHash d = getRandomHash();
    ChainHash e = getRandomHash();
    ChainHash f = getRandomHash();
    ChainHash g = getRandomHash();
    ChainHash h = getRandomHash();
    ChainHash i = getRandomHash();
    
    ChainHash result = DigestUtil.getMerkleRootForTxList(ImmutableList.of(a, b, c, d, e, f, g, h, i));

    ChainHash ab = treeHash(a, b);
    ChainHash cd = treeHash(c, d);
    ChainHash ef = treeHash(e, f);
    ChainHash gh = treeHash(g, h);

    ChainHash abcd = treeHash(ab, cd);
    ChainHash efgh = treeHash(ef, gh);

    ChainHash abcdefgh = treeHash(abcd, efgh);

    ChainHash r = treeHash(abcdefgh, i);

    Assert.assertEquals(r, result);
  }








  private ChainHash getRandomHash()
  {
    byte[] b = new byte[Globals.BLOCKCHAIN_HASH_LEN];
    rnd.nextBytes(b);

    return new ChainHash(b);

  }

  private ChainHash treeHash(ChainHash a, ChainHash b)
  {
    MessageDigest md = DigestUtil.getMD();
    md.update(a.toByteArray());
    md.update(b.toByteArray());

    ChainHash r = new ChainHash(md.digest());

    System.out.println("" +a + " + " + b + " -> " + r);

    return r;
  }
  
}
