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


public class GetUTXOUtil
{

  private static final Logger logger = Logger.getLogger("snowblossom.client");

  public static List<TransactionBridge> getSpendableValidated(AddressSpecHash addr, UserServiceBlockingStub stub, ByteString utxo_root )
    throws ValidationException
  {
    logger.log(Level.FINE,String.format("Get Spendable (%s, %s)", addr.toString(), HexUtil.getHexString(utxo_root)));
    HashMap<ByteString, TrieNode> node_map = new HashMap<>(10000,0.5f);
    LinkedList<TransactionBridge> bridges = new LinkedList<>();

    for(TrieNode n : getNodesByPrefix(addr.getBytes(), stub, true, utxo_root))
    {
      node_map.put(n.getPrefix(), n);
    }

    descend(ByteString.EMPTY, addr.getBytes(), stub, bridges, node_map, utxo_root, utxo_root);

    logger.log(Level.FINE, String.format("Get Spendable %s: %d nodes, %d bridges", addr.toString(), node_map.size(), bridges.size()));

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
