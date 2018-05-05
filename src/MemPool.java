package snowblossom;

import com.google.common.collect.TreeMultimap;

import snowblossom.proto.TransactionMempoolInfo;
import snowblossom.proto.Transaction;
import snowblossom.proto.TransactionInput;
import snowblossom.proto.TransactionInner;
import snowblossom.trie.HashedTrie;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Collection;
import java.util.TreeSet;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;

/**
 * This class has the potential to be the most complex here.  
 *  There are quite a number of concerns to keep straight.  Fortunately, making sure the resulting
 * blocks are valid is responcibility of other code.  Also, we don't need the perfect selection of 
 * transactions, just need it to be workable and valid.
 *
 * Objectives:
 *  - Sort by (fee/tx size) for priority
 *  - The first seen valid transaction to try to spend a tx_out should have priority (avoids double spends)
 *  - If a transaction depends on a currently unconfirmed transaction, consider them both for the same block
 *    - Note: could be more than a chain of two, ideally we'd support any length chain because that is fun
 *  - Prune out impossible transactions as soon as is reasonable
 *  - If we have never heard of the inputs to a tx, drop it
 */
public class MemPool
{
  private Map<ChainHash, TransactionMempoolInfo> known_transactions = new HashMap<>(512, 0.5f);

  private HashMap<String, ChainHash> claimed_outputs = new HashMap<>();


  // In normal operation, the priority map is updated as transactions come in
  // However, when a new block is learned we need to toss it all and start
  // fresh.  Since we don't want to require a transaction index for the entire chain
  // it isn't easy to see which transactions we should exclude because they are in blocks
  // already.  So we toss the priority map and build a new one from known_transactions.
  // Anything from known_transactions that can't be put in the priority map is tossed.
  //
  // In theory, we can just take everything in the priority map (excluding duplicate transactions)
  // since a tx could be needed for multiple clusters and make a block out of it.
  //
  // Easy as eating pancakes.
  // 
  private ChainHash utxo_for_pri_map = null;
  private TreeMultimap<Double, TXCluster> priority_map = TreeMultimap.<Double, TXCluster>create();

  private HashedTrie utxo_hashed_trie;

  public MemPool(HashedTrie utxo_hashed_trie)
  {
    this.utxo_hashed_trie = utxo_hashed_trie;
  }

  public synchronized List<Transaction> getTransactionsForBlock(ChainHash last_utxo, int max_size)
  {
    List<Transaction> block_list = new ArrayList<Transaction>();
    Set<ChainHash> included_txs = new HashSet<>();


    if (last_utxo != utxo_for_pri_map)
    {
      rebuildPriorityMap(last_utxo);
    }

    int size = 0;

    TreeMultimap<Double, TXCluster> priority_map_copy = TreeMultimap.<Double, TXCluster>create();
    priority_map_copy.putAll(priority_map);

    while(priority_map_copy.size() > 0)
    {
      Collection<TXCluster> list = priority_map_copy.asMap().pollLastEntry().getValue();
      for(TXCluster cluster : list)
      {
        if (size + cluster.total_size <= max_size)
        {
          for(Transaction tx : cluster.tx_list)
          {
            ChainHash tx_hash = new ChainHash(tx.getTxHash());
            if (!included_txs.contains(tx_hash))
            {
              block_list.add(tx);
              included_txs.add(tx_hash);
              size += tx.toByteString().size();
            }
          }
          
        }
      }
    }
    return block_list;

  }

  public synchronized void addTransaction(Transaction tx)
    throws ValidationException
  {
    Validation.checkTransactionBasics(tx, false);
    ChainHash tx_hash = new ChainHash(tx.getTxHash());
    if (known_transactions.containsKey(tx_hash)) return;

    TransactionMempoolInfo info = TransactionMempoolInfo.newBuilder()
      .setTx(tx)
      .setSeen(System.currentTimeMillis())
      .build();
    
    TransactionInner inner = TransactionUtil.getInner(tx);
    TreeSet<String> used_outputs = new TreeSet<>();

    for(TransactionInput in : inner.getInputsList())
    {
      String key = HexUtil.getHexString( in.getSrcTxId() ) + ":" + in.getSrcTxOutIdx();
      used_outputs.add(key);

      if (claimed_outputs.containsKey(key))
      {
        if (!claimed_outputs.get(key).equals(tx_hash))
        {
          throw new ValidationException("Discarding as double-spend");
        }
      }
    }

    if (utxo_for_pri_map != null)
    {
      TXCluster cluster = buildTXCluster(tx);
      if (cluster == null)
      {
        throw new ValidationException("Unable to find a tx cluster that makes this work");
      }
      double ratio = (double)cluster.total_fee / (double)cluster.total_size;
      priority_map.put(ratio, cluster);

    }
    
    known_transactions.put(tx_hash, info);
    // Claim outputs used by inputs
    for(String key : used_outputs)
    {
      claimed_outputs.put(key, tx_hash);
    }
  }

  public synchronized void rebuildPriorityMap(ChainHash new_utxo_root)
  {
    utxo_for_pri_map = new_utxo_root;
    priority_map.clear();

    LinkedList<ChainHash> remove_list = new LinkedList<>();

    for(TransactionMempoolInfo info : known_transactions.values())
    {
      Transaction tx = info.getTx();
      TXCluster cluster;
      try
      {
        cluster = buildTXCluster(tx);
      }
      catch(ValidationException e)
      {
        cluster = null;
      }

      if (cluster == null)
      {
        remove_list.add(new ChainHash(tx.getTxHash()));
        TransactionInner inner = TransactionUtil.getInner(tx);
        for(TransactionInput in : inner.getInputsList())
        {
          String key = HexUtil.getHexString( in.getSrcTxId() ) + ":" + in.getSrcTxOutIdx();
          claimed_outputs.remove(key);
        }
      }
      else
      {
        double ratio = (double)cluster.total_fee / (double)cluster.total_size;
        priority_map.put(ratio, cluster);
      }
    }

    for(ChainHash h : remove_list)
    {
      known_transactions.remove(h);

    }

  }

  /**
   * Attemped to build an ordered list of transactions
   * that can confirm.  In the simple case, it is just
   * a single transaction that has all outputs already in utxo.
   * In the more complex case, a chain of transactions needs to go
   * in for the transaction in question to be confirmed.
   */
  private TXCluster buildTXCluster(Transaction target_tx)
    throws ValidationException
  {
    System.out.println("--------------------------------------------------------------");
    HashSet<ChainHash> working_set = new HashSet<>();
    
    LinkedList<Transaction> working_list = new LinkedList<>();
    working_list.add(target_tx);
    working_set.add(new ChainHash(target_tx.getTxHash()));

    TreeMap<ChainHash, Integer> level_map = new TreeMap<>();
    level_map.put(new ChainHash(target_tx.getTxHash()), 0);

    int added = 1;

    // The case where nothing is added but we are still here is that
    // We built a list of transactions that *should* work but it is 
    // still failing deep validation, which means amounts of maybe output indexes
    // or number of times spending each output (lol) don't match up and never will.
    while(added > 0)
    {
      System.out.println("Working list: " + working_list);
      System.out.println("Working set: " + working_set);
      added=0;
      try
      {
        UtxoUpdateBuffer test_buffer = new UtxoUpdateBuffer(utxo_hashed_trie, utxo_for_pri_map);
        for(Transaction t : working_list)
        {
          Validation.deepTransactionCheck(t, test_buffer);
        }
        return new TXCluster(working_list);
      }
      catch(ValidationException ve){
        System.out.println(ve);
      }

      LinkedList<Transaction> add_list = new LinkedList<>();
     
      for(Transaction t : working_list)
      {
        TransactionInner inner = TransactionUtil.getInner(t);
        for(TransactionInput in : inner.getInputsList())
        {
          ChainHash needed_tx = new ChainHash(in.getSrcTxId());
          System.out.println("Trying to find tx: " + needed_tx);
          int needed_level = level_map.get(new ChainHash(t.getTxHash())) - 1;

          if ((!level_map.containsKey(needed_tx)) || (level_map.get(needed_tx) > needed_level))
          {
            level_map.put(needed_tx, needed_level);
            added++;
          }

          if (!working_set.contains(needed_tx))
          {
            ByteString key = UtxoUpdateBuffer.getKey(in);

            ByteString matching_output = utxo_hashed_trie.get(utxo_for_pri_map.getBytes(), key);
            if (matching_output == null)
            {
              System.out.println("not in utxo");
              if (known_transactions.containsKey(needed_tx))
              {
                System.out.println("Adding " + needed_tx);
                add_list.add(known_transactions.get(needed_tx).getTx());
                working_set.add(needed_tx);
                System.out.println("Added from known");
                added++;
              }
              else
              {
                throw new ValidationException("Can't find source tx: " + needed_tx.toString());
              }
            }
            else
            {
              System.out.println("It as in utxo already");
              working_set.add(needed_tx); //it is in utxo, no need to keep looking
            }
          }
          else
          {
            System.out.println("Already in working set");
          }
        }
      }

      for(Transaction tx : add_list)
      {
        working_list.push(tx);
      }
      //Reorder working list

      //System.out.println("Level map: " + level_map);

      TreeMap<ChainHash, Transaction> tx_map = new TreeMap<>();
      for(Transaction tx : working_list)
      {
        tx_map.put(new ChainHash(tx.getTxHash()), tx);
      }
      TreeMultimap<Integer, ChainHash> order_map = TreeMultimap.<Integer, ChainHash>create();

      for(Map.Entry<ChainHash, Integer> me : level_map.entrySet())
      {
        if (tx_map.containsKey(me.getKey()))
        {
          order_map.put(me.getValue(), me.getKey());
        }
      }
      working_list.clear();
      //System.out.println("Order map: " + order_map);
      while(order_map.size() > 0)
      {
        for(ChainHash hash : order_map.asMap().pollFirstEntry().getValue())
        {
          working_list.add( tx_map.get( hash ) );
        }
      }

      //
    }

    return null;


  }


  /** 
   * A list of transactions which depend on each other.
   * The list should be ordered such that if they are added in the same order
   * that would be valid for a block.
   *
   * Putting them in a cluster allows them to be considered as a bundle for the purpose of fee priority.
   * Allows for child pays for parent fee style.
   */
  public class TXCluster implements Comparable<TXCluster>
  {
    ImmutableList<Transaction> tx_list;
    int total_size;
    long total_fee;

    public TXCluster(List<Transaction> tx_in_list)
    {
      tx_list = ImmutableList.copyOf(tx_in_list);

      for(Transaction t : tx_in_list)
      {
        total_size += t.toByteString().size();

        TransactionInner inner = TransactionUtil.getInner(t);
        total_fee += inner.getFee();
      }
    }
    
    public int compareTo(TXCluster o)
    {
      return tx_list.toString().compareTo(o.tx_list.toString());
    }

  }

  

}
