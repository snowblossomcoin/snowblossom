package miner.test;


import com.google.protobuf.ByteString;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import snowblossom.lib.Validation;
import snowblossom.proto.SnowPowProof;
import snowblossom.lib.Globals;
import snowblossom.lib.SnowFall;
import snowblossom.lib.SnowMerkle;
import snowblossom.miner.SnowMerkleProof;

import java.io.File;
import java.security.MessageDigest;
import java.util.LinkedList;
import java.util.Random;

public class SnowMerkleProofTest
{
  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  private void testProofSingle(int mb_size, String seed, long location)
    throws Exception
  {
    File tmp_dir = testFolder.newFolder();
        
    long byte_len = mb_size * 1048576L;
    File deck = new File(tmp_dir, "test.deck.a");
    File snow = new File(tmp_dir, "test.snow");


    new SnowFall(snow.getAbsolutePath(), seed, byte_len);

    ByteString root_hash = new SnowMerkle(
      tmp_dir, "test", true).getRootHash();

    SnowMerkleProof proofGen = new SnowMerkleProof(tmp_dir, "test");
    
    SnowPowProof proof = proofGen.getProof(location);

    Assert.assertEquals(location, proof.getWordIdx());
    System.out.println(proof);
    
    Assert.assertTrue(Validation.checkProof(proof, root_hash, byte_len));


  }
  private void testProofMany(int mb_size, String seed, int count)
    throws Exception
  {
    File tmp_dir = testFolder.newFolder();
        
    long byte_len = mb_size * 1048576L;
    File deck = new File(tmp_dir, "test.deck.a");
    File snow = new File(tmp_dir, "test.snow");


    new SnowFall(snow.getAbsolutePath(), seed, byte_len);

    ByteString root_hash = new SnowMerkle(
      tmp_dir, "test", true).getRootHash();

    SnowMerkleProof proofGen = new SnowMerkleProof(tmp_dir, "test");
    
    int words = (int) (byte_len / SnowMerkle.HASH_LEN_LONG);
    Random rnd = new Random(byte_len);

    for(int i=0; i<count; i++)
    {
      long location = rnd.nextInt(words);
    
      SnowPowProof proof = proofGen.getProof(location);

      Assert.assertEquals(location, proof.getWordIdx());
    
      Assert.assertTrue(Validation.checkProof(proof, root_hash, byte_len));
    }

  }

  @Test
  public void testProofShortStack()
    throws Exception
  {
    long location = 0;
    int mb_size = 1;
    String seed = "zing";

    File tmp_dir = testFolder.newFolder();
        
    long byte_len = mb_size * 1048576L;
    File deck = new File(tmp_dir, "test.deck.a");
    File snow = new File(tmp_dir, "test.snow");

    new SnowFall(snow.getAbsolutePath(), seed, byte_len);

    ByteString root_hash = new SnowMerkle(tmp_dir, "test" , true).getRootHash();

    SnowMerkleProof proofGen = new SnowMerkleProof(tmp_dir, "test");
    
    SnowPowProof real_proof = proofGen.getProof(location);

    SnowPowProof.Builder fake_proof = SnowPowProof.newBuilder();
    fake_proof.setWordIdx(location);
    LinkedList<ByteString> fiends = new LinkedList<ByteString>();
    fiends.addAll(real_proof.getMerkleComponentList());

    MessageDigest md = MessageDigest.getInstance(Globals.SNOW_MERKLE_HASH_ALGO);
    md.update(fiends.poll().toByteArray());
    md.update(fiends.poll().toByteArray());

    fiends.push(ByteString.copyFrom(md.digest()));
    fake_proof.addAllMerkleComponent(fiends);

    Assert.assertFalse(Validation.checkProof(fake_proof.build(), root_hash, byte_len));

  }

  @Test
  public void test1MB() throws Exception
  {
    testProofSingle(1, "zing", 0);
    testProofSingle(1, "zing", 65535);
    testProofSingle(1, "zing", 32768);
  }

  @Test
  public void testMany() throws Exception
  {
    testProofMany(2, "zing", 200);
  }

}

