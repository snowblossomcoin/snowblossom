package snowblossom.client;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.trie.proto.*;
import snowblossom.lib.trie.HashUtils;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import com.google.protobuf.ByteString;

import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.common.collect.ImmutableList;
import duckutil.LRUCache;

public class GetUTXOUtil
{
  public static final long UTXO_ROOT_EXPIRE=500L; 

  private static final Logger logger = Logger.getLogger("snowblossom.client");

	// Saves cache of address,utxo_root to list of bridges, which will
  // never change for that given input pair
  private LRUCache<String, List<TransactionBridge> > spendable_cache = new LRUCache<>(1000);

  private UserServiceBlockingStub stub;
	private long utxo_root_time = 0;
  private ChainHash last_utxo_root = null;
  private NetworkParams params;

  public GetUTXOUtil(UserServiceBlockingStub stub, NetworkParams params)
  {
    this.stub = stub;
    this.params = params;
  }

  public synchronized ChainHash getCurrentUtxoRootHash()
  {
		
		if (utxo_root_time + UTXO_ROOT_EXPIRE < System.currentTimeMillis())
		{
      NodeStatus ns = stub.getNodeStatus( NullRequest.newBuilder().build() );
      if (ns.getNetwork().length() > 0)
      {
        if (!ns.getNetwork().equals(params.getNetworkName()))
        {
          throw new RuntimeException(String.format("Network name mismatch.  Expected %s, got %s",
            params.getNetworkName(),
            ns.getNetwork()));
        }

      }

    	last_utxo_root= new ChainHash(ns.getHeadSummary().getHeader().getUtxoRootHash());
			utxo_root_time = System.currentTimeMillis();
      logger.log(Level.FINE, "UTXO root hash: " + last_utxo_root);
		}
		return last_utxo_root;
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

    for(ByteString tx_hash : stub.getMempoolTransactionList(
      RequestAddress.newBuilder().setAddressSpecHash(addr.getBytes()).build()).getTxHashesList())
    { 
      Transaction tx = stub.getTransaction(RequestTransaction.newBuilder().setTxHash(tx_hash).build());

      TransactionInner inner = TransactionUtil.getInner(tx);

      for(TransactionInput in : inner.getInputsList())
      { 
        if (addr.equals(in.getSpecHash()))
        { 
          TransactionBridge b_in = new TransactionBridge(in);
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
          TransactionBridge b_out = new TransactionBridge(out, o, new ChainHash(tx_hash));
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
    return bridge_map;

  }

  public List<TransactionBridge> getSpendableValidated(AddressSpecHash addr)
		throws ValidationException
  {
		ChainHash utxo_root = getCurrentUtxoRootHash();
    String key = addr.toString() + "/" + utxo_root;
		synchronized(spendable_cache)
		{
			List<TransactionBridge> lst = spendable_cache.get(key);
			if (lst != null) return lst;
		}

		List<TransactionBridge> lst = getSpendableValidatedStatic(addr, stub, utxo_root.getBytes());
		
		lst = ImmutableList.copyOf(lst);
		synchronized(spendable_cache)
		{
			spendable_cache.put(key, lst);
		}
		return lst;

  }
  public static List<TransactionBridge> getSpendableValidatedStatic(AddressSpecHash addr, UserServiceBlockingStub stub, ByteString utxo_root )
    throws ValidationException
  {
    logger.log(Level.FINE,String.format("Get Spendable (%s, %s)", addr.toString(), HexUtil.getHexString(utxo_root)));
    return getSpendableValidatedStatic(addr.getBytes(), stub, utxo_root);
    

  }

  public static List<TransactionBridge> getSpendableValidatedStatic(ByteString prefix, UserServiceBlockingStub stub, ByteString utxo_root )
    throws ValidationException
  {
    HashMap<ByteString, TrieNode> node_map = new HashMap<>(10000,0.5f);
    LinkedList<TransactionBridge> bridges = new LinkedList<>();

    for(TrieNode n : getNodesByPrefix(prefix, stub, true, utxo_root))
    {
      node_map.put(n.getPrefix(), n);
    }

    descend(ByteString.EMPTY, prefix, stub, bridges, node_map, utxo_root, utxo_root);
    
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
    ByteString utxo_root)
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
      bridges.add(new TransactionBridge(node));
    }
    
    for(ChildEntry ce : node.getChildrenList())
    {
      ByteString next = prefix.concat(ce.getKey());
      if (next.size() <= search.size())
      {
        if (search.startsWith(next))
        {
          descend(next, search, stub, bridges, node_map, ce.getHash(), utxo_root);
        }
      }
      if (next.size() > search.size())
      {
        if (next.startsWith(search))
        {
          descend(next, search, stub, bridges, node_map, ce.getHash(), utxo_root);
        }
      }
    }

  }
  
  private static List<TrieNode> getNodesByPrefix(ByteString prefix, UserServiceBlockingStub stub, boolean proof, ByteString utxo_root)
    throws ValidationException
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
