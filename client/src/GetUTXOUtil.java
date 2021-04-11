package snowblossom.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import duckutil.LRUCache;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.lib.trie.HashUtils;
import snowblossom.proto.*;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.trie.proto.*;

public class GetUTXOUtil
{
  public static final long UTXO_ROOT_EXPIRE=500L; 

  private static final Logger logger = Logger.getLogger("snowblossom.client");

  // Saves cache of address,utxo_root to list of bridges, which will
  // never change for that given input pair
  private LRUCache<String, List<TransactionBridge> > spendable_cache = new LRUCache<>(10000);

  private StubHolder stub_holder;

  private long utxo_shard_time = 0;
  private ImmutableMap<Integer, ChainHash> utxo_shard_map = null;

  private NetworkParams params;

  public GetUTXOUtil(StubHolder stub_holder, NetworkParams params)
  {
    this.stub_holder = stub_holder;
    this.params = params;
  }


  public synchronized Map<Integer,ChainHash> getCurrentUtxoShardHashes()
  {
    if (utxo_shard_time + UTXO_ROOT_EXPIRE < System.currentTimeMillis())
    {
      try(TimeRecordAuto tra = TimeRecord.openAuto("GetUTXOUtil.UtxoHashesUpdate"))
      {
        NodeStatus ns = getStub().getNodeStatus( NullRequest.newBuilder().build() );
        if (ns.getNetwork().length() > 0)
        {
          if (!ns.getNetwork().equals(params.getNetworkName()))
          {
            throw new RuntimeException(String.format("Network name mismatch.  Expected %s, got %s",
              params.getNetworkName(),
              ns.getNetwork()));
          }
        }

        TreeMap<Integer, ChainHash> utxo_map = new TreeMap<>();
        for(Map.Entry<Integer, BlockSummary> me : ns.getShardSummaryMap().entrySet())
        {

          utxo_map.put(me.getKey(), new ChainHash(me.getValue().getHeader().getUtxoRootHash()));
        }

        utxo_shard_map = ImmutableMap.copyOf(utxo_map);
        utxo_shard_time = System.currentTimeMillis();

        logger.log(Level.FINE, "UTXO shard map: " + utxo_shard_map);
      }
      
    }
    return utxo_shard_map;
  }




  private UserServiceBlockingStub getStub()
  {
    return stub_holder.getBlockingStub();
  }

  private static LRUCache<ChainHash, Transaction> tx_cache = new LRUCache<>(100000);
  private Transaction getTransaction(ChainHash tx_hash)
  {
    try(TimeRecordAuto tra = TimeRecord.openAuto("GetUTXOUtil.getTransaction"))
    {
      synchronized(tx_cache)
      {
        Transaction tx = tx_cache.get(tx_hash);
        if (tx != null) return tx;
      }
    
      Transaction tx;
      try(TimeRecordAuto tra_actual = TimeRecord.openAuto("GetUTXOUtil.getTransaction_actual"))
      {
        tx = getStub().getTransaction(RequestTransaction.newBuilder().setTxHash(tx_hash.getBytes()).build());
      }

      if (tx != null)
      {
        synchronized(tx_cache)
        {
          tx_cache.put(tx_hash, tx);
        }
      }

      return tx;
    }
  }

  /**
   * Add a transaction to cache we are likely to care about in the future.
   * really only useful to avoid getTransaction calls in high call rate operations
   * like load tests
   */
  public void cacheTransaction(Transaction tx)
  {
    synchronized(tx_cache)
    {
      tx_cache.put( new ChainHash( tx.getTxHash() ), tx);
    }
  }

  public Map<String, TransactionBridge> getSpendableWithMempool(AddressSpecHash addr)
    throws ValidationException
  {

    List<TransactionBridge> confirmed_bridges = getSpendableValidated(addr);

    HashMap<String, TransactionBridge> bridge_map=new HashMap<>();
    for(TransactionBridge b : confirmed_bridges)
    {   
        bridge_map.put(b.getKeyString(), b);
    }

    
    Map<Integer, TransactionHashList> tx_shard_map;
    try(TimeRecordAuto tra = TimeRecord.openAuto("GetUTXOUtil.getSpendableWithMempool_mempool"))
    {
      tx_shard_map = getStub().getMempoolTransactionMap( 
        RequestAddress.newBuilder().setAddressSpecHash(addr.getBytes()).build()).getShardMap();
    }

    try(TimeRecordAuto tra = TimeRecord.openAuto("GetUTXOUtil.getSpendableWithMempool_slice"))
    {
      for(Map.Entry<Integer, TransactionHashList> me : tx_shard_map.entrySet())
      {
        int shard_id = me.getKey();

        for(ByteString tx_hash : me.getValue().getTxHashesList())
        { 
          Transaction tx = getTransaction(new ChainHash(tx_hash));

          TransactionInner inner = TransactionUtil.getInner(tx);

          for(TransactionInput in : inner.getInputsList())
          { 
            if (addr.equals(in.getSpecHash()))
            { 
              TransactionBridge b_in = new TransactionBridge(in, shard_id);
              String key = b_in.getKeyString();
              if (bridge_map.containsKey(key))
              { 
                bridge_map.get(key).spent=true;
              }
              else
              {
                bridge_map.put(key, b_in);
              }
            }
          }
          for(int o=0; o<inner.getOutputsCount(); o++)
          { 
            TransactionOutput out = inner.getOutputs(o);
            if (addr.equals(out.getRecipientSpecHash()))
            {
              // This transaction is in the mempool, so it isn't on a shard yet
              // but what shards it ends up on is not as simple as one could be hoped
              // So if this tx is confirmed, only the outputs on it that go in this 
              // shard (shard_id) will be immediately spendable
              if (ShardUtil.getCoverSet( shard_id, params).contains(out.getTargetShard()))
              {
                TransactionBridge b_out = new TransactionBridge(out, o, new ChainHash(tx_hash), shard_id);
                String key = b_out.getKeyString();
                b_out.unconfirmed=true;

                if (bridge_map.containsKey(key))
                { 
                  if (bridge_map.get(key).spent)
                  {
                    b_out.spent=true;
                  }
                }
                bridge_map.put(key, b_out);
              }
            }
          }
        }
      }
    }
    return bridge_map;

  }

  public List<TransactionBridge> getSpendableValidated(AddressSpecHash addr)
    throws ValidationException
  {
    Map<Integer, ChainHash> utxo_map = getCurrentUtxoShardHashes();
    List<TransactionBridge> big_lst = new LinkedList<>();

    for(Map.Entry<Integer, ChainHash> me : utxo_map.entrySet())
    {
      int shard_id = me.getKey(); 
      ChainHash utxo_root = me.getValue();
      String key = addr.toString() + "/" + shard_id + "/" + utxo_root;
      List<TransactionBridge> lst = null;
      synchronized(spendable_cache)
      {
        lst = spendable_cache.get(key);
      }
      if (lst == null)
      {
        lst = getSpendableValidatedStatic(addr, getStub(), utxo_root.getBytes(), shard_id);
        lst = ImmutableList.copyOf(lst);
        synchronized(spendable_cache)
        {
          spendable_cache.put(key, lst);
        }
      }
      big_lst.addAll(lst);

    }
    return big_lst;

  }

  public static List<TransactionBridge> getSpendableValidatedStatic(AddressSpecHash addr, 
    UserServiceBlockingStub stub, ByteString utxo_root, int shard_id )
    throws ValidationException
  {
    logger.log(Level.FINE,String.format("Get Spendable (%s, %s)", addr.toString(), HexUtil.getHexString(utxo_root)));
    return getSpendableValidatedStatic(addr.getBytes(), stub, utxo_root, shard_id);
    

  }

  public static List<TransactionBridge> getSpendableValidatedStatic(ByteString prefix, 
    UserServiceBlockingStub stub, ByteString utxo_root, int shard_id )
    throws ValidationException
  {
    HashMap<ByteString, TrieNode> node_map = new HashMap<>(10000,0.5f);
    LinkedList<TransactionBridge> bridges = new LinkedList<>();

    for(TrieNode n : getNodesByPrefix(prefix, stub, true, utxo_root))
    {
      node_map.put(n.getPrefix(), n);
    }

    descend(ByteString.EMPTY, prefix, stub, bridges, node_map, utxo_root, utxo_root, shard_id);
    
    logger.log(Level.FINE, String.format("Get Spendable: %d nodes, %d bridges", node_map.size(), bridges.size()));

    return bridges;

  }

  private static void descend(
    ByteString prefix, 
    ByteString search, 
    UserServiceBlockingStub stub,
    List<TransactionBridge> bridges,
    Map<ByteString, TrieNode> node_map,
    ByteString expected_hash,
    ByteString utxo_root,
    int shard_id)
    throws ValidationException
  {
    if (prefix.size() > search.size())
    {
      if (!node_map.containsKey(prefix))
      {
        logger.log(Level.FINE,"Doing additional scan into " + HexUtil.getHexString(prefix) + ".");
        for(TrieNode n : getNodesByPrefix(prefix, stub, false, utxo_root))
        {
          node_map.put(n.getPrefix(), n);
        }
      }
    }

    if (!node_map.containsKey(prefix))
    {
      throw new ValidationException("No node at prefix: " + HexUtil.getHexString(prefix) + ".");
    }

    TrieNode node = node_map.get(prefix);
    if (!node.getHash().equals(expected_hash))
    {
      throw new ValidationException("Hash mismatch at prefix: " + HexUtil.getHexString(prefix) + ".");
    }
    if (node.getIsLeaf())
    {
      bridges.add(new TransactionBridge(node, shard_id));
    }
    
    for(ChildEntry ce : node.getChildrenList())
    {
      ByteString next = prefix.concat(ce.getKey());
      if (next.size() <= search.size())
      {
        if (search.startsWith(next))
        {
          descend(next, search, stub, bridges, node_map, ce.getHash(), utxo_root, shard_id);
        }
      }
      if (next.size() > search.size())
      {
        if (next.startsWith(search))
        {
          descend(next, search, stub, bridges, node_map, ce.getHash(), utxo_root, shard_id);
        }
      }
    }

  }
  
  private static List<TrieNode> getNodesByPrefix(ByteString prefix, UserServiceBlockingStub stub, boolean proof, ByteString utxo_root)
    throws ValidationException
  {
    try(TimeRecordAuto tra = TimeRecord.openAuto("GetUTXOUtil.getNodesByPrefix"))
    {
      LinkedList<TrieNode> lst = new LinkedList<>();
      GetUTXONodeReply reply = stub.getUTXONode( GetUTXONodeRequest.newBuilder()
        .setPrefix(prefix)
        .setIncludeProof(proof)
        .setUtxoRootHash(utxo_root)
        .setMaxResults(10000)
        .build());
      for(TrieNode node : reply.getAnswerList())
      {
        if (!HashUtils.validateNodeHash(node)) throw new ValidationException("Validation failure in node: " + HexUtil.getHexString(node.getPrefix()));
        lst.add(node);
      }
      for(TrieNode node : reply.getProofList())
      {
        if (!HashUtils.validateNodeHash(node)) throw new ValidationException("Validation failure in node: " + HexUtil.getHexString(node.getPrefix()));
        lst.add(node);
      }


      return lst;
    }
  }


}
