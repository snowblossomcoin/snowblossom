package lib.test.trie;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import snowblossom.lib.trie.HashUtils;
import snowblossom.lib.trie.HashedTrie;
import snowblossom.lib.trie.TrieDBRocks;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;


public class TrieRocksTest
{
  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();


  private HashedTrie trie;
  private TrieDBRocks db;

  @Before
  public void setupDB()
    throws Exception
  {
    File db_dir = testFolder.newFolder();
    
    db = new TrieDBRocks(db_dir);
    trie = new HashedTrie(db, 8, true);
  }

  @After
  public void closeDB()
  {
    // If we don't do a close, I think we get strange aborts like this:
    //  pure virtual method called
    //  terminate called without an active exception
    // I think this is from when TemporaryFolder rule is cleaning up Rocks DB
    // files while RocksDB is still flushing
    // In general use, you wouldn't want to open and close all these databases like this

    trie = null;
    db.flush();
    db = null;
  }

  private static ByteString emptyRoot = HashUtils.hashOfEmpty();

  @Test
  public void testCreateEmpty() throws Exception {

    Map<ByteString, ByteString> update_map = new HashMap<>();

    ByteString hash = trie.mergeBatch(emptyRoot, update_map);

    ByteString expected_hash = HashUtils.hashConcat(ImmutableList.of(ByteString.EMPTY));

    Assert.assertEquals(expected_hash, hash);
  }

  @Test
  public void testCreateSingle() throws Exception {

    Map<ByteString, ByteString> update_map = new HashMap<>();

    ByteString key = ByteString.copyFrom(new String("baseball").getBytes());
    ByteString data = ByteString.copyFrom(new String("data").getBytes());

    update_map.put(key, data);

    ByteString hash = trie.mergeBatch(emptyRoot, update_map);

    ByteString subkey_hash = HashUtils.hashConcat(ImmutableList.of(key, data));

    ByteString expected_hash = HashUtils.hashConcat(ImmutableList.of(key, subkey_hash));

    Assert.assertEquals(expected_hash, hash);

  }
  @Test
  public void testCreateSingleAndRemove() throws Exception {

    Map<ByteString, ByteString> update_map = new HashMap<>();

    ByteString key = ByteString.copyFrom(new String("baseball").getBytes());
    ByteString data = ByteString.copyFrom(new String("data").getBytes());

    update_map.put(key, data);

    ByteString hash = trie.mergeBatch(emptyRoot, update_map);

    ByteString subkey_hash = HashUtils.hashConcat(ImmutableList.of(key, data));

    ByteString expected_hash = HashUtils.hashConcat(ImmutableList.of(key, subkey_hash));

    Assert.assertEquals(expected_hash, hash);
  }
  @Test
  public void testCreateDouble() throws Exception {

    Map<ByteString, ByteString> update_map = new HashMap<>();

    ByteString key1 = ByteString.copyFrom(new String("baseball").getBytes());
    ByteString data1 = ByteString.copyFrom(new String("data").getBytes());

    ByteString key2 = ByteString.copyFrom(new String("basewall").getBytes());
    ByteString data2 = ByteString.copyFrom(new String("1938281113112223111").getBytes());
    update_map.put(key1, data1);
    update_map.put(key2, data2);

    ByteString base = ByteString.copyFrom(new String("base").getBytes());
    ByteString ball = ByteString.copyFrom(new String("ball").getBytes());
    ByteString wall = ByteString.copyFrom(new String("wall").getBytes());

    ByteString r1 = trie.mergeBatch(emptyRoot, update_map);

    ByteString subkey1_hash = HashUtils.hashConcat(ImmutableList.of(key1, data1));
    ByteString subkey2_hash = HashUtils.hashConcat(ImmutableList.of(key2, data2));

    ByteString sub_base_hash = HashUtils.hashConcat(ImmutableList.of(base, ball, subkey1_hash, wall, subkey2_hash));

    ByteString expected_hash = HashUtils.hashConcat(ImmutableList.of(base, sub_base_hash));

    Assert.assertEquals(expected_hash, r1);

    update_map.clear();
    update_map.put(key1, null);

    ByteString expected_hash_after_delete = HashUtils.hashConcat(ImmutableList.of(key2, subkey2_hash));

    
    ByteString r2 = trie.mergeBatch(r1, update_map);
    Assert.assertEquals(expected_hash_after_delete, r2);

  }
  @Test
  public void testCreateDoubleTwoStep() throws Exception {

    Map<ByteString, ByteString> update_map = new HashMap<>();

    ByteString key1 = ByteString.copyFrom(new String("baseball").getBytes());
    ByteString data1 = ByteString.copyFrom(new String("data").getBytes());

    ByteString key2 = ByteString.copyFrom(new String("basewall").getBytes());
    ByteString data2 = ByteString.copyFrom(new String("1938281113112223111").getBytes());
    update_map.put(key1, data1);
    
    ByteString r1 = trie.mergeBatch(emptyRoot, update_map);
    update_map.clear();

    update_map.put(key2, data2);

    ByteString base = ByteString.copyFrom(new String("base").getBytes());
    ByteString ball = ByteString.copyFrom(new String("ball").getBytes());
    ByteString wall = ByteString.copyFrom(new String("wall").getBytes());

    ByteString r2 = trie.mergeBatch(r1, update_map);

    ByteString subkey1_hash = HashUtils.hashConcat(ImmutableList.of(key1, data1));
    ByteString subkey2_hash = HashUtils.hashConcat(ImmutableList.of(key2, data2));

    ByteString sub_base_hash = HashUtils.hashConcat(ImmutableList.of(base, ball, subkey1_hash, wall, subkey2_hash));

    ByteString expected_hash = HashUtils.hashConcat(ImmutableList.of(base, sub_base_hash));

    Assert.assertEquals(expected_hash, r2);

    update_map.clear();
    update_map.put(key1, null);

    ByteString expected_hash_after_delete = HashUtils.hashConcat(ImmutableList.of(key2, subkey2_hash));

    ByteString r3 = trie.mergeBatch(r2, update_map);
    Assert.assertEquals(expected_hash_after_delete, r3);

  }


  @Test
  public void testCreateRandom() throws Exception {
 
    Map<ByteString, ByteString> update_map = new HashMap<>();

    Random rnd = new Random(87L);

    for(int i=0; i<10000; i++)
    {
      byte[] key_data = new byte[8];
      rnd.nextBytes(key_data);
      ByteString key = ByteString.copyFrom(key_data);

      byte[] data_data = new byte[8];
      rnd.nextBytes(data_data);
      ByteString data = ByteString.copyFrom(data_data);

      update_map.put(key, data);
    }
    ByteString hash = trie.mergeBatch(emptyRoot, update_map);
    String hash_str= HashUtils.getHexString(hash);

    Assert.assertEquals("c2a8b068d8613232723c54d611faf9bc894adbd5b36c089fb3ab0379415978f3",hash_str);
  }
  @Test
  public void testCreateRandomChunks() throws Exception {
 
    Map<ByteString, ByteString> update_map = new HashMap<>();

    Random rnd = new Random(87L);

    ByteString last_root = emptyRoot;

    for(int i=0; i<10000; i++)
    {
      byte[] key_data = new byte[8];
      rnd.nextBytes(key_data);
      ByteString key = ByteString.copyFrom(key_data);

      byte[] data_data = new byte[8];
      rnd.nextBytes(data_data);
      ByteString data = ByteString.copyFrom(data_data);

      update_map.put(key, data);

      if (i%100 ==0)
      {
        last_root = trie.mergeBatch(last_root, update_map);
        update_map.clear();
      }
    }
    ByteString hash = trie.mergeBatch(last_root, update_map);
    String hash_str= HashUtils.getHexString(hash);

    Assert.assertEquals("c2a8b068d8613232723c54d611faf9bc894adbd5b36c089fb3ab0379415978f3",hash_str);
  }


  @Test
  public void testCreateRandomMultiway() throws Exception {
 
    Map<ByteString, ByteString> update_map = new HashMap<>();

    Random rnd = new Random(87L);

    for(int i=0; i<10000; i++)
    {
      byte[] key_data = new byte[8];
      rnd.nextBytes(key_data);
      ByteString key = ByteString.copyFrom(key_data);

      byte[] data_data = new byte[8];
      rnd.nextBytes(data_data);
      ByteString data = ByteString.copyFrom(data_data);

      update_map.put(key, data);
    }

    ByteString first_hash = trie.mergeBatch(emptyRoot, update_map);

    //memdb = new TrieDBMem();
    //trie = new HashedTrie(memdb, 8, true);
    Map<ByteString, ByteString> remove_map = new HashMap<>();
    rnd = new Random(87L);
    for(int i=0; i<17000; i++)
    {
      byte[] key_data = new byte[8];
      rnd.nextBytes(key_data);
      ByteString key = ByteString.copyFrom(key_data);

      byte[] data_data = new byte[8];
      rnd.nextBytes(data_data);
      ByteString data = ByteString.copyFrom(data_data);

      update_map.put(key, data);
      if (i>=10000) remove_map.put(key, null);
    }
    ByteString hash_with_things = trie.mergeBatch(emptyRoot, update_map);

    Assert.assertNotEquals(first_hash, hash_with_things);
    ByteString second_hash = trie.mergeBatch(hash_with_things, remove_map);
    Assert.assertEquals(first_hash, second_hash);
    
    String hash_str= HashUtils.getHexString(first_hash);
    Assert.assertEquals("c2a8b068d8613232723c54d611faf9bc894adbd5b36c089fb3ab0379415978f3",hash_str);
    
  }

}

