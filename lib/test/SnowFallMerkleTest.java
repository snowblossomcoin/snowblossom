package lib.test;


import org.apache.commons.codec.binary.Hex;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import snowblossom.lib.SnowFall;
import snowblossom.lib.SnowMerkle;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.HashMap;

public class SnowFallMerkleTest
{
  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  private void testSize(int mb_size, String seed, String expected)
    throws Exception
  {
    File tmp_dir = testFolder.newFolder();

    new SnowFall(tmp_dir.getAbsolutePath() +"/test.snow", seed, mb_size * 1048576L);

    String hash = new SnowMerkle(tmp_dir, "test", true).getRootHashStr();

    Assert.assertEquals(expected, hash);
    File deck = new File(tmp_dir, "test.deck.a");
    File snow = new File(tmp_dir, "test.snow");
    Assert.assertTrue(deck.exists());
    Assert.assertTrue(snow.exists());

    checkFile(snow);

    deck.renameTo(new File(tmp_dir, "snowdeck.snow"));

    // The deck file should have the exact same merkle root hash
    String deckhash = new SnowMerkle(tmp_dir, "snowdeck", false).getRootHashStr();
    Assert.assertEquals(expected, deckhash);

    if (SnowMerkle.getNumberOfDecks(mb_size * 1048576L / SnowMerkle.HASH_LEN_LONG) > 1)
    {
      File deckb = new File(tmp_dir, "test.deck.b");
      Assert.assertTrue(deckb.exists());
      deckb.renameTo(new File(tmp_dir, "snowdeckb.snow"));

      String deckhashb = new SnowMerkle(tmp_dir, "snowdeckb", false).getRootHashStr();
      Assert.assertEquals(expected, deckhashb);
    }
    
    

  }


  @Test
  public void test1MB() throws Exception
  {
    testSize(1, "zing", "c58564b6208329ae2317ec606bbc7f4a");
  }

  @Test
  public void test8MB() throws Exception
  {
    testSize(8, "zing", "10d0a3be329d4b490d18c6295b08f77e");
  }

  @Test
  public void test64MB() throws Exception
  {
    testSize(64, "zing", "f8ca73a8cc7076dc9823caebbcadbf79");
  }
 
  @Test
  public void testTeapot8MB() throws Exception
  {
    testSize(8, "teapot.3", "97f8303394d267dfe4bf65243bd3740e");
  }
  private void checkFile(File f)
    throws Exception
  {

    MessageDigest md = MessageDigest.getInstance("SHA-256");

    DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f),1048576));

    byte[] buff = new byte[SnowFall.PAGESIZE];

    long total_len = f.length();

    HashMap<String, Long> block_hashes = new HashMap<String, Long>(1024, 0.5f);

    long reads = total_len / SnowFall.PAGESIZE;
    for(long r=0; r<reads; r++)
    {
      in.readFully(buff);
      md.update(buff);
      byte[] h = md.digest();
      String hex_str= Hex.encodeHexString(h);

      Assert.assertFalse(block_hashes.containsKey(hex_str));
      block_hashes.put(hex_str, r);

      int zeros  = 0;
      for(int i=0; i<buff.length; i++)
      {
        if (buff[i] == 0) zeros++;
      }
      Assert.assertTrue(zeros < buff.length);

    }
    Assert.assertEquals((int)reads, block_hashes.size());

    in.close();
  }

  @Test
  public void testDeckCountRegular()
  {
    Assert.assertEquals(1, SnowMerkle.getNumberOfDecks(4096));
    Assert.assertEquals(3, SnowMerkle.getNumberOfDecks(4096L * 4096L * 4096L));
    Assert.assertEquals(3, SnowMerkle.getNumberOfDecks(4096L * 4096L * 4096L * 2L));

  }
 
  
}

