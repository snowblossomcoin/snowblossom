package node.test;

import com.google.common.collect.ImmutableList;
import duckutil.TimeRecord;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import snowblossom.proto.AddressSpec;
import snowblossom.proto.Transaction;
import snowblossom.proto.TransactionInput;
import snowblossom.proto.TransactionOutput;
import snowblossom.lib.trie.HashedTrie;
import snowblossom.lib.trie.TrieDBMem;
import snowblossom.node.MemPool;
import snowblossom.node.ChainStateSource;
import snowblossom.lib.*;

import java.security.KeyPair;
import java.util.Random;
import java.util.TreeMap;

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

    MemPool mem_pool = new MemPool(utxo_trie, new DummyChainState(100));

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

    MemPool mem_pool = new MemPool(utxo_trie, new DummyChainState(100));

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
      Assert.assertTrue(e.getMessage(), e.getMessage().startsWith("Unable to find source tx"));
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

    MemPool mem_pool = new MemPool(utxo_trie, new DummyChainState(100));

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

    Transaction tx = TransactionUtil.createTransaction(ImmutableList.of(in, in), ImmutableList.of(out), keys);

    MemPool mem_pool = new MemPool(utxo_trie, new DummyChainState(100));

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

    MemPool mem_pool = new MemPool(utxo_trie, new DummyChainState(100));

    mem_pool.rebuildPriorityMap(utxo_root);

    Assert.assertEquals(0, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());

    mem_pool.addTransaction(tx_a);

    Assert.assertEquals(1, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());

    TransactionInput in_b = TransactionInput.newBuilder()
      .setSpecHash(in_a.getSpecHash())
      .setSrcTxId(tx_a.getTxHash())
      .setSrcTxOutIdx(0)
      .build();
 
    TransactionOutput out_b = TransactionOutput.newBuilder()
      .setRecipientSpecHash(in_a.getSpecHash())
      .setValue(100000L)
      .build();

    Transaction tx_b = TransactionUtil.createTransaction(ImmutableList.of(in_b), ImmutableList.of(out_b), keys);

    mem_pool.addTransaction(tx_b);
    
    Assert.assertEquals(2, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());
    
    TransactionInput in_c = TransactionInput.newBuilder()
      .setSpecHash(in_a.getSpecHash())
      .setSrcTxId(tx_b.getTxHash())
      .setSrcTxOutIdx(0)
      .build();
 
    TransactionOutput out_c = TransactionOutput.newBuilder()
      .setRecipientSpecHash(in_a.getSpecHash())
      .setValue(100000L)
      .build();

    Transaction tx_c = TransactionUtil.createTransaction(ImmutableList.of(in_c), ImmutableList.of(out_c), keys);

    mem_pool.addTransaction(tx_c);
    
    Assert.assertEquals(3, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());
  }

  @Test
  public void testStormChain() throws Exception
  {

    HashedTrie utxo_trie = newMemoryTrie();
    KeyPair keys = KeyUtil.generateECCompressedKey();

    UtxoUpdateBuffer utxo_buffer = new UtxoUpdateBuffer(utxo_trie, UtxoUpdateBuffer.EMPTY);

    Random rnd = new Random();

    TreeMap<Double, InputInfo> ready_inputs = new TreeMap<>();
    AddressSpecHash address = null;

    for(int i=0; i<100; i++)
    {
      TransactionInput in = addUtxoToUseAtInput(utxo_buffer, keys, 100000L);

      System.out.println("Source tx: " + new ChainHash(in.getSrcTxId()));
      InputInfo ii = new InputInfo();
      ii.in = in;
      ii.value = 100000L;
      ready_inputs.put(rnd.nextDouble(), ii);

      address = new AddressSpecHash(in.getSpecHash());
    }
    
    ChainHash utxo_root = utxo_buffer.commit();

    MemPool mem_pool = new MemPool(utxo_trie, new DummyChainState(100), 10000000);
    Assert.assertEquals(0, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());

    TimeRecord tr = new TimeRecord();
    TimeRecord.setSharedRecord(tr);
    for(int i=0; i<500; i++)
    {
      long t1= System.nanoTime();

      InputInfo ia = ready_inputs.pollFirstEntry().getValue();
      InputInfo ib = ready_inputs.pollFirstEntry().getValue();
      InputInfo ic = ready_inputs.pollFirstEntry().getValue();

      TransactionOutput out = TransactionOutput.newBuilder()
        .setRecipientSpecHash(address.getBytes())
        .setValue(100000L)
        .build();

      System.out.println("Selected inputs: " + ia + " " + ib + " " + ic);

      long t3=System.nanoTime();
      Transaction tx = TransactionUtil.createTransaction(ImmutableList.of(ia.in, ib.in, ic.in), ImmutableList.of(out, out, out), keys);
      TimeRecord.record(t3,"create_tx");
      System.out.println("Intermediate transaction: " + new ChainHash(tx.getTxHash()));

      long t2=System.nanoTime();
      mem_pool.addTransaction(tx);
      TimeRecord.record(t2, "mem_pool");
    

      for(int j=0; j<3; j++)
      {
        TransactionInput in = TransactionInput.newBuilder()
          .setSpecHash(address.getBytes())
          .setSrcTxId(tx.getTxHash())
          .setSrcTxOutIdx(j)
          .build();
        System.out.println(String.format("Adding output %s:%d", new ChainHash(tx.getTxHash()).toString(), j));

        InputInfo ii = new InputInfo();
        ii.in = in;
        ii.value = 100000L;
        ready_inputs.put(rnd.nextDouble(), ii);
      }

      TimeRecord.record(t1, "tx");


    }
      long t4=System.nanoTime();
      Assert.assertEquals(500, mem_pool.getTransactionsForBlock(utxo_root, 1048576).size());
      TimeRecord.record(t4,"get_block_tx_list");
    tr.printReport(System.out);

  }

  public class InputInfo
  {
    TransactionInput in;
    long value;

    public String toString()
    {
      return String.format("%s:%d - %d", new ChainHash(in.getSrcTxId()).toString(), in.getSrcTxOutIdx(), value);
    }
  }

  public static TransactionInput addUtxoToUseAtInput(UtxoUpdateBuffer utxo_buffer, KeyPair keys, long value)
    throws Exception  
  {
    Random rnd = new Random();
    byte[] tx_id_buff = new byte[Globals.BLOCKCHAIN_HASH_LEN];
    rnd.nextBytes(tx_id_buff);

    ChainHash tx_id = new ChainHash(tx_id_buff);

    AddressSpec claim = AddressUtil.getSimpleSpecForKey(keys.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);

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

    utxo_buffer.addOutput(ImmutableList.of(tx_out.toByteString()) , tx_out, tx_id, 0);


    return tx_in;
			

  }

  public static HashedTrie newMemoryTrie()
  {
    return new HashedTrie(new TrieDBMem(), Globals.UTXO_KEY_LEN, true);
  }

  public class DummyChainState implements ChainStateSource
  {
    private int height;
    public DummyChainState(int height)
    {
      this.height = height;
    }

    @Override
    public int getHeight() {return height; }
    
    @Override
    public NetworkParams getParams() {return new NetworkParamsRegtest(); }
    
  }

}

