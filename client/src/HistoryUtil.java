package snowblossom.client;

import com.google.protobuf.ByteString;
import duckutil.LRUCache;
import duckutil.Pair;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.ChainHash;
import snowblossom.lib.Globals;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.TransactionUtil;
import snowblossom.proto.*;
import snowblossom.proto.WalletDatabase;

public class HistoryUtil
{

  private static LRUCache<ChainHash, Transaction> tx_cache = new LRUCache<>(100000);

  private static Transaction getTx(ChainHash tx_hash, SnowBlossomClient client)
  {
    synchronized(tx_cache)
    {
      Transaction tx = tx_cache.get(tx_hash);
      if (tx != null) return tx;
    }
    
    Transaction tx = client.getStub().getTransaction( RequestTransaction.newBuilder().setTxHash(tx_hash.getBytes()).build() );

    synchronized(tx_cache)
    {
      tx_cache.put(tx_hash, tx);
    }
    return tx;

  }

  public static void printHistory(PrintStream out, WalletDatabase db, NetworkParams params, SnowBlossomClient client)
  {
    HashSet<AddressSpecHash> all = new HashSet<>();
    all.addAll( WalletUtil.getAddressesByAge(db, params) );

    TreeSet<Pair<Integer, ChainHash>> transaction_history = new TreeSet<>();
    TreeSet<ChainHash> mempool_list = new TreeSet<>();

    boolean incomplete_no_history = false;

    // If this gets super slow, parallelize this loop
    for(AddressSpecHash hash : all)
    {
      HistoryList hl = client.getStub().getAddressHistory(
        RequestAddress.newBuilder().setAddressSpecHash(hash.getBytes()).build());
      if (hl.getNotEnabled())
      {
        incomplete_no_history=true;
      }
      for(HistoryEntry e : hl.getEntriesList())
      {
        int height = e.getBlockHeight();
        ChainHash tx_hash = new ChainHash(e.getTxHash());
        transaction_history.add(new Pair(height, tx_hash));
      }

      TransactionHashList tl = client.getStub().getMempoolTransactionList(
        RequestAddress.newBuilder().setAddressSpecHash(hash.getBytes()).build());
      for(ByteString h : tl.getTxHashesList())
      {
        ChainHash tx_hash = new ChainHash(h);
        mempool_list.add(tx_hash);
      }
    }
    if (incomplete_no_history)
    {
      out.println("THIS HISTORY IS MISSING - CONNECTED NODE DOES NOT SUPPORT ADDRESS HISTORY");
    }

    for(ChainHash tx_hash : mempool_list)
    {
      Transaction tx = getTx(tx_hash, client);

      out.println(String.format("pending / %s", getSummaryLine(tx, all, params, client)));
    }
    for(Pair<Integer, ChainHash> p : transaction_history.descendingSet())
    {
      int block = p.getA();
      ChainHash tx_hash = p.getB();
      
      Transaction tx = getTx(tx_hash, client);
      out.println(String.format("block %d / %s", block, getSummaryLine(tx, all, params, client)));

    }
    

  }

  /**
   * Returns a single line summary of a transaction with respect to a wallet.
   * If the only non-wallet address is an output, it will show as a payment out to that address
   * If the only wallet address is an output, then it will be shown as a payment in to that address
   * If all addresses are wallet addresses it will be shown as internal
   * If no addresses are wallet addresses it will be shown as external (strange case)
   * Otherwise it will show as "complex"
   */
  public static String getSummaryLine(Transaction tx, Set<AddressSpecHash> wallet_addresses, NetworkParams params, SnowBlossomClient client)
  {
    ChainHash tx_hash = new ChainHash(tx.getTxHash());
    TransactionInner inner = TransactionUtil.getInner(tx);
    long delta = 0L;

    AddressSpecHash non_wallet_output = null;
    AddressSpecHash wallet_output = null;
    int wallet_addr = 0;
    int non_wallet_addr = 0;

    AddressSpecHash action_addr = null;
    String mode = "complex";

    for(TransactionInput in : inner.getInputsList())
    {
      AddressSpecHash addr = new AddressSpecHash(in.getSpecHash());
      double v = getValue(in, client);
      if (wallet_addresses.contains(addr))
      {
        delta -=v;
        wallet_addr++;
      }
      else
      {
        non_wallet_addr++;
      }
    }
    for(TransactionOutput out : inner.getOutputsList())
    {
      AddressSpecHash addr = new AddressSpecHash(out.getRecipientSpecHash());
      double v = out.getValue();
      if (wallet_addresses.contains(addr))
      {
        delta += v;
        wallet_addr++;
        wallet_output = addr;
      }
      else
      {
        non_wallet_addr++;
        non_wallet_output = addr;
      }
    }
    if (inner.getIsCoinbase())
    {
      if (wallet_output != null)
      {
        action_addr = wallet_output;
      }
      mode = "block reward";
    }
    else if ((wallet_addr == 1) && (wallet_output != null))
    {
      action_addr = wallet_output;
      mode = "payment in";
    }
    else if ((non_wallet_addr == 1) && (non_wallet_output != null))
    {
      action_addr = non_wallet_output;
      mode = "payment out";
    }
    else if (wallet_addr == 0)
    {
      mode = "external";
    }
    else if (non_wallet_addr == 0)
    {
      mode = "internal";
    }
    
    DecimalFormat df = new DecimalFormat("0.000000");
    String val = df.format(delta / Globals.SNOW_VALUE_D);

    String action = "";
    if (action_addr != null)
    {
      action = action_addr.toAddressString(params);
    }

    return String.format("%s SNOW / %s / %s / %s", val, tx_hash.toString(), mode, action);
  }


  public static long getValue(TransactionInput in, SnowBlossomClient client)
  {
    Transaction src = getTx(new ChainHash(in.getSrcTxId()), client);

    TransactionInner inner = TransactionUtil.getInner(src);

    return inner.getOutputs(in.getSrcTxOutIdx()).getValue();


  }


 


}
