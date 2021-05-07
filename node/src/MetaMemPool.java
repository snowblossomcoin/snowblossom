package snowblossom.node;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.ChainHash;
import snowblossom.lib.Validation;
import snowblossom.lib.ValidationException;
import snowblossom.proto.Transaction;

public class MetaMemPool
{
  private SnowBlossomNode node;
  private static final Logger logger = Logger.getLogger("snowblossom.mempool");

  public MetaMemPool(SnowBlossomNode node)
  {
    this.node = node;
  }


  /**
   * Try to feed the transaction to each shard.  If any take it, return true.
   */
  public boolean addTransaction(Transaction tx, boolean p2p_source) throws ValidationException
  {
    Validation.checkTransactionBasics(tx, false);
    for(int s : node.getCurrentBuildingShards())
    {
      try
      {
        MemPool mp = node.getMemPool(s);
        if (mp.addTransaction(tx, p2p_source)) return true;
      }
      catch(ValidationException e)
      { // to be expected
        logger.log(Level.FINER, "pool rejected",e);
  
      }
    }
    return false;
  }

  public void registerListener(MemPoolTickleInterface listener)
  {
    for(int s : node.getCurrentBuildingShards())
    {
      node.getMemPool(s).registerListener(listener);
    }

  }

  public int getMemPoolSize()
  {
    int sum =0;
    for(int s : node.getCurrentBuildingShards())
    {
      sum += node.getMemPool(s).getMemPoolSize();
    }
    return sum;
  }

  public Map<Integer, Set<ChainHash> > getTransactionsForAddressByShard(AddressSpecHash spec_hash)
  {
    TreeMap<Integer, Set<ChainHash> > m = new TreeMap<>();

    for(int s : node.getCurrentBuildingShards())
    {
      if (!m.containsKey(s)) m.put(s, new HashSet<ChainHash>());
      m.get(s).addAll(node.getMemPool(s).getTransactionsForAddress(spec_hash));
    }
    return m;
  }


  public Set<ChainHash> getTransactionsForAddress(AddressSpecHash spec_hash)
  {
    HashSet<ChainHash> set = new HashSet<>();

    for(int s : node.getCurrentBuildingShards())
    {
      set.addAll(node.getMemPool(s).getTransactionsForAddress(spec_hash));
    }
    return set;
  }

  public Transaction getTransaction(ChainHash tx_hash)
  {
    for(int s : node.getCurrentBuildingShards())
    {
      Transaction tx = node.getMemPool(s).getTransaction(tx_hash);
      if (tx != null) return tx;
    }
    return null;
  }

  public List<Transaction> getTxClusterForTransaction(ChainHash tx_id)
  {
    for(int s : node.getCurrentBuildingShards())
    {
      List<Transaction> tx_lst= node.getMemPool(s).getTxClusterForTransaction(tx_id);
      if (tx_lst != null) return tx_lst;
    }
    return null;
  }


}
