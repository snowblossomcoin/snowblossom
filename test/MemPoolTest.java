package snowblossom;

import org.junit.Assert;
import org.junit.Test;
import org.junit.BeforeClass;

import snowblossom.trie.HashedTrie;
import snowblossom.trie.TrieDBMem;

import java.security.KeyPair;
import java.util.Random;

import snowblossom.proto.Transaction;
import snowblossom.proto.TransactionInput;
import snowblossom.proto.TransactionOutput;
import snowblossom.proto.AddressSpec;
import snowblossom.proto.SigSpec;

import com.google.protobuf.ByteString;
import com.google.common.collect.ImmutableList;

public class MemPoolTest
{

  @BeforeClass
  public static void loadProvider()
  {
    Globals.addCryptoProvider();
  }

  @Test
  public void testBasicTxYes()
    throws Exception
  {
    HashedTrie utxo_trie = newMemoryTrie();
    KeyPair keys = KeyUtil.generateECCompressedKey();

    UtxoUpdateBuffer utxo_buffer = new UtxoUpdateBuffer(utxo_trie, UtxoUpdateBuffer.EMPTY);
    
    TransactionInput in = addUtxoToUseAtInput(utxo_buffer, keys, 100000L);

    ChainHash utxo_root = utxo_buffer.commit();

    TransactionOutput out = TransactionOutput.newBuilder()
      .setRecipientSpecHash(in.getSpecHash())
      .setValue(100000L)
      .build();

    Transaction tx = TransactionUtil.createTransaction(ImmutableList.of(in), ImmutableList.of(out), keys);

    MemPool mem_pool = new MemPool(utxo_trie);

    mem_pool.rebuildPriorityMap(utxo_root);

    // Pool starts empty
    Assert.assertEquals(0, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());

    mem_pool.addTransaction(tx);

    // Then has our transaction
    Assert.assertEquals(1, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());
    
    mem_pool.rebuildPriorityMap(utxo_root);

    // Still has our transaction
    Assert.assertEquals(1, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());
    
    mem_pool.rebuildPriorityMap(UtxoUpdateBuffer.EMPTY);
    
    // That transaction is impossible now
    Assert.assertEquals(0, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());
    
    mem_pool.rebuildPriorityMap(utxo_root);
    
    // And does not come back
    Assert.assertEquals(0, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());


    TransactionOutput out_a = TransactionOutput.newBuilder()
      .setRecipientSpecHash(in.getSpecHash())
      .setValue(50000L)
      .build();

    Transaction tx2 = TransactionUtil.createTransaction(ImmutableList.of(in), ImmutableList.of(out_a, out_a), keys);

    Assert.assertNotEquals(tx.getTxHash(), tx2.getTxHash());

    mem_pool.addTransaction(tx2);
   
    // And the outputs are freed up
    Assert.assertEquals(1, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());
  }

  @Test
  public void testBasicTxNoInput()
    throws Exception
  {
    HashedTrie utxo_trie = newMemoryTrie();
    KeyPair keys = KeyUtil.generateECCompressedKey();

    UtxoUpdateBuffer utxo_buffer = new UtxoUpdateBuffer(utxo_trie, UtxoUpdateBuffer.EMPTY);
    
    TransactionInput in = addUtxoToUseAtInput(utxo_buffer, keys, 100000L);

    ChainHash utxo_root = UtxoUpdateBuffer.EMPTY;

    TransactionOutput out = TransactionOutput.newBuilder()
      .setRecipientSpecHash(in.getSpecHash())
      .setValue(100000L)
      .build();

    Transaction tx = TransactionUtil.createTransaction(ImmutableList.of(in), ImmutableList.of(out), keys);

    MemPool mem_pool = new MemPool(utxo_trie);

    mem_pool.rebuildPriorityMap(utxo_root);

    // Pool starts empty
    Assert.assertEquals(0, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());

    try
    {
      mem_pool.addTransaction(tx);
      Assert.fail();
    }
    catch(ValidationException e)
    {
      Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("Can't find source tx"));
    }

  }

  @Test
  public void testBasicTxDoubleSpend()
    throws Exception
  {
    HashedTrie utxo_trie = newMemoryTrie();
    KeyPair keys = KeyUtil.generateECCompressedKey();

    UtxoUpdateBuffer utxo_buffer = new UtxoUpdateBuffer(utxo_trie, UtxoUpdateBuffer.EMPTY);
    
    TransactionInput in = addUtxoToUseAtInput(utxo_buffer, keys, 100000L);

    ChainHash utxo_root = utxo_buffer.commit();

    TransactionOutput out = TransactionOutput.newBuilder()
      .setRecipientSpecHash(in.getSpecHash())
      .setValue(100000L)
      .build();

    Transaction tx = TransactionUtil.createTransaction(ImmutableList.of(in), ImmutableList.of(out), keys);

    MemPool mem_pool = new MemPool(utxo_trie);

    mem_pool.rebuildPriorityMap(utxo_root);

    // Pool starts empty
    Assert.assertEquals(0, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());

    mem_pool.addTransaction(tx);
    TransactionOutput out_a = TransactionOutput.newBuilder()
      .setRecipientSpecHash(in.getSpecHash())
      .setValue(50000L)
      .build();

    Transaction tx2 = TransactionUtil.createTransaction(ImmutableList.of(in), ImmutableList.of(out_a, out_a), keys);

    Assert.assertNotEquals(tx.getTxHash(), tx2.getTxHash());

    try
    {
      mem_pool.addTransaction(tx2);
      Assert.fail();
    }
    catch(ValidationException e){System.out.println(e);}

  }


  @Test
  public void testBasicTxNoDoubleInput()
    throws Exception
  {
    HashedTrie utxo_trie = newMemoryTrie();
    KeyPair keys = KeyUtil.generateECCompressedKey();

    UtxoUpdateBuffer utxo_buffer = new UtxoUpdateBuffer(utxo_trie, UtxoUpdateBuffer.EMPTY);
    
    TransactionInput in = addUtxoToUseAtInput(utxo_buffer, keys, 100000L);

    ChainHash utxo_root = utxo_buffer.commit();

    TransactionOutput out = TransactionOutput.newBuilder()
      .setRecipientSpecHash(in.getSpecHash())
      .setValue(200000L)
      .build();

    Transaction tx = TransactionUtil.createTransaction(ImmutableList.of(in,in), ImmutableList.of(out), keys);

    MemPool mem_pool = new MemPool(utxo_trie);

    mem_pool.rebuildPriorityMap(utxo_root);

    // Pool starts empty
    Assert.assertEquals(0, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());

    try
    {
      mem_pool.addTransaction(tx);
      Assert.fail();
    }
    catch(ValidationException e){System.out.println(e);}

  }


  @Test
  public void testSimpleChain() throws Exception
  {
    HashedTrie utxo_trie = newMemoryTrie();
    KeyPair keys = KeyUtil.generateECCompressedKey();

    UtxoUpdateBuffer utxo_buffer = new UtxoUpdateBuffer(utxo_trie, UtxoUpdateBuffer.EMPTY);
    
    TransactionInput in_a = addUtxoToUseAtInput(utxo_buffer, keys, 100000L);

    ChainHash utxo_root = utxo_buffer.commit();

    TransactionOutput out_a = TransactionOutput.newBuilder()
      .setRecipientSpecHash(in_a.getSpecHash())
      .setValue(100000L)
      .build();

    Transaction tx_a = TransactionUtil.createTransaction(ImmutableList.of(in_a), ImmutableList.of(out_a), keys);

    MemPool mem_pool = new MemPool(utxo_trie);

    mem_pool.rebuildPriorityMap(utxo_root);

    Assert.assertEquals(0, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());

    mem_pool.addTransaction(tx_a);

    Assert.assertEquals(1, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());
 

  }



  public static TransactionInput addUtxoToUseAtInput(UtxoUpdateBuffer utxo_buffer, KeyPair keys, long value)
  {
    Random rnd = new Random();
    byte[] tx_id_buff = new byte[Globals.BLOCKCHAIN_HASH_LEN];
    rnd.nextBytes(tx_id_buff);

    ChainHash tx_id = new ChainHash(tx_id_buff);

    byte[] public_key = keys.getPublic().getEncoded();

    AddressSpec claim = AddressSpec.newBuilder()
      .setRequiredSigners(1)
      .addSigSpecs( SigSpec.newBuilder()
        .setSignatureType( SignatureUtil.SIG_TYPE_ECDSA)
        .setPublicKey(ByteString.copyFrom(public_key))
        .build())
      .build();

    AddressSpecHash addr = AddressUtil.getHashForSpec(claim);

		TransactionInput tx_in = TransactionInput.newBuilder()
			.setSpecHash(addr.getBytes())
      .setSrcTxId(tx_id.getBytes())
      .setSrcTxOutIdx(0)
      .build();

    TransactionOutput tx_out = TransactionOutput.newBuilder()
      .setRecipientSpecHash(addr.getBytes())
      .setValue(value)
      .build();

    utxo_buffer.addOutput(tx_out, tx_id, 0);


    return tx_in;
			

  }



  public static HashedTrie newMemoryTrie()
  {
    return new HashedTrie(new TrieDBMem(), Globals.UTXO_KEY_LEN, true);
  }

}

