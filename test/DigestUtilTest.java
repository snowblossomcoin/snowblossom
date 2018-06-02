package snowblossom;

import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import snowblossomlib.ChainHash;
import snowblossomlib.DigestUtil;
import snowblossomlib.Globals;

import java.security.MessageDigest;
import java.util.Random;

public class DigestUtilTest
{
  private Random rnd = new Random();

  @BeforeClass
  public static void loadProvider()
  {
    snowblossomlib.Globals.addCryptoProvider();
  }


  @Test
  public void testMerkleRootSingle()
  {
    snowblossomlib.ChainHash a = getRandomHash();
    
    snowblossomlib.ChainHash result = snowblossomlib.DigestUtil.getMerkleRootForTxList(ImmutableList.of(a));

    Assert.assertEquals(a, result);
  }

  @Test
  public void testMerkleRoot2()
  {
    snowblossomlib.ChainHash a = getRandomHash();
    snowblossomlib.ChainHash b = getRandomHash();
    
    snowblossomlib.ChainHash result = snowblossomlib.DigestUtil.getMerkleRootForTxList(ImmutableList.of(a, b));

    snowblossomlib.ChainHash ab = treeHash(a, b);

    Assert.assertEquals(ab, result);
  }

  @Test
  public void testMerkleRoot3()
  {
    snowblossomlib.ChainHash a = getRandomHash();
    snowblossomlib.ChainHash b = getRandomHash();
    snowblossomlib.ChainHash c = getRandomHash();
    
    snowblossomlib.ChainHash result = snowblossomlib.DigestUtil.getMerkleRootForTxList(ImmutableList.of(a, b, c));

    snowblossomlib.ChainHash ab = treeHash(a, b);
    snowblossomlib.ChainHash ab_c = treeHash(ab, c);

    Assert.assertEquals(ab_c, result);
  }

  @Test
  public void testMerkleRoot8()
  {
    snowblossomlib.ChainHash a = getRandomHash();
    snowblossomlib.ChainHash b = getRandomHash();
    snowblossomlib.ChainHash c = getRandomHash();
    snowblossomlib.ChainHash d = getRandomHash();
    snowblossomlib.ChainHash e = getRandomHash();
    snowblossomlib.ChainHash f = getRandomHash();
    snowblossomlib.ChainHash g = getRandomHash();
    snowblossomlib.ChainHash h = getRandomHash();
    
    snowblossomlib.ChainHash result = snowblossomlib.DigestUtil.getMerkleRootForTxList(ImmutableList.of(a, b, c, d, e, f, g, h));

    snowblossomlib.ChainHash ab = treeHash(a, b);
    snowblossomlib.ChainHash cd = treeHash(c, d);
    snowblossomlib.ChainHash ef = treeHash(e, f);
    snowblossomlib.ChainHash gh = treeHash(g, h);

    snowblossomlib.ChainHash abcd = treeHash(ab, cd);
    snowblossomlib.ChainHash efgh = treeHash(ef, gh);


    snowblossomlib.ChainHash abcdefgh = treeHash(abcd, efgh);

    Assert.assertEquals(abcdefgh, result);
  }
  @Test
  public void testMerkleRoot9()
  {
    snowblossomlib.ChainHash a = getRandomHash();
    snowblossomlib.ChainHash b = getRandomHash();
    snowblossomlib.ChainHash c = getRandomHash();
    snowblossomlib.ChainHash d = getRandomHash();
    snowblossomlib.ChainHash e = getRandomHash();
    snowblossomlib.ChainHash f = getRandomHash();
    snowblossomlib.ChainHash g = getRandomHash();
    snowblossomlib.ChainHash h = getRandomHash();
    snowblossomlib.ChainHash i = getRandomHash();
    
    snowblossomlib.ChainHash result = snowblossomlib.DigestUtil.getMerkleRootForTxList(ImmutableList.of(a, b, c, d, e, f, g, h, i));

    snowblossomlib.ChainHash ab = treeHash(a, b);
    snowblossomlib.ChainHash cd = treeHash(c, d);
    snowblossomlib.ChainHash ef = treeHash(e, f);
    snowblossomlib.ChainHash gh = treeHash(g, h);

    snowblossomlib.ChainHash abcd = treeHash(ab, cd);
    snowblossomlib.ChainHash efgh = treeHash(ef, gh);

    snowblossomlib.ChainHash abcdefgh = treeHash(abcd, efgh);

    snowblossomlib.ChainHash r = treeHash(abcdefgh, i);

    Assert.assertEquals(r, result);
  }








  private snowblossomlib.ChainHash getRandomHash()
  {
    byte[] b = new byte[Globals.BLOCKCHAIN_HASH_LEN];
    rnd.nextBytes(b);

    return new snowblossomlib.ChainHash(b);

  }

  private snowblossomlib.ChainHash treeHash(snowblossomlib.ChainHash a, snowblossomlib.ChainHash b)
  {
    MessageDigest md = DigestUtil.getMD();
    md.update(a.toByteArray());
    md.update(b.toByteArray());

    snowblossomlib.ChainHash r = new ChainHash(md.digest());

    System.out.println("" +a + " + " + b + " -> " + r);

    return r;
  }
  
}
