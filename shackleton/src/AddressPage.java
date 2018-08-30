package snowblossom.shackleton;

import com.google.protobuf.ByteString;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.trie.proto.TrieNode;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Collections;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.client.GetUTXOUtil;

public class AddressPage
{
  private final PrintStream out;
  private final AddressSpecHash address;
  private final NetworkParams params;
  private final UserServiceBlockingStub stub;
  private final DecimalFormat df = new DecimalFormat("0.000000");
  private final boolean want_history;
  private final GetUTXOUtil get_utxo_util;
 

  public AddressPage(PrintStream out, AddressSpecHash address, NetworkParams params, UserServiceBlockingStub stub, boolean want_history, GetUTXOUtil get_utxo_util)
  {
    this.out = out;
    this.address = address;
    this.params = params;
    this.stub = stub;
    this.want_history = want_history;
    this.get_utxo_util = get_utxo_util;
  }

  public void render()
  {
    out.println("<p>Address: " + AddressUtil.getAddressString(params.getAddressPrefix(), address) + "</p>");
    loadData();

    double val_conf_d = (double) valueConfirmed / (double) Globals.SNOW_VALUE;
    double val_unconf_d = (double) valueUnconfirmed / (double) Globals.SNOW_VALUE;
    out.println("<H2>Balance</H2>");
    out.println(String.format("<p>%s (%s pending) in %d outputs</p>", df.format(val_conf_d), df.format(val_unconf_d), totalOutputs));

    if (want_history)
    {
      out.println("<H2>Mempool</H2>");
      for(ByteString tx_hash : mempool_list.getTxHashesList())
      {
        String tx = HexUtil.getHexString(tx_hash);

        out.println(String.format("<li>tx: <a href='/?search=%s'>%s</a> </li>",
          tx, tx));
      }

      out.println("<H2>History</H2>");

      LinkedList<HistoryEntry> entries = new LinkedList<>();
      entries.addAll(history.getEntriesList());
      Collections.reverse(entries);
      for(HistoryEntry he : entries)
      {
        int height =  he.getBlockHeight();
        ChainHash tx_hash = new ChainHash(he.getTxHash());

        out.println(String.format("<li> Block: <a href='/?search=%d'>%d</a> - tx: <a href='/?search=%s'>%s</a> </li>",
          height, height,
          tx_hash, tx_hash));
      }
    }
    
/*
    out.println("<table>");
    out.println(" <thead>");
    out.println("   <th></th>");
    out.println(" </thead>");
    out.println(" <tbody>");
    for (TransactionBridge bridge : bridges)
    {
      out.println(bridge.in.getSrcTxId()
    }
    out.println(" </tbody>");
    out.println("</table>");

*/
  }

  long valueConfirmed = 0;
  long valueUnconfirmed = 0;
  long totalSpendable = 0;
  long totalOutputs;
  List<TransactionBridge> bridges;
  HistoryList history;
  TransactionHashList mempool_list;

  protected void loadData()
  {

    try
    {
      bridges = getSpendable(address);
      if (want_history)
      {
        history = getHistory(address);
        mempool_list = stub.getMempoolTransactionList(RequestAddress.newBuilder().setAddressSpecHash(address.getBytes()).build()); 
      }
      for (TransactionBridge b : bridges)
      {

        if (b.unconfirmed)
        {
          if (!b.spent)
          {
            valueUnconfirmed += b.value;
          }
        }
        else //confirmed
        {
          valueConfirmed += b.value;
          if (b.spent)
          {
            valueUnconfirmed -= b.value;
          }
        }
        if (!b.spent)
        {
          totalSpendable += b.value;
        }
        totalOutputs++;
      }
    }
    catch (Throwable e)
    {
      e.printStackTrace(out);
    }
  }

  public HistoryList getHistory(AddressSpecHash addr)
  {
    return stub.getAddressHistory(RequestAddress.newBuilder().setAddressSpecHash(addr.getBytes()).build());

  }

  public List<TransactionBridge> getSpendable(AddressSpecHash addr)
    throws ValidationException
  {

    LinkedList<TransactionBridge> lst = new LinkedList<>();
    lst.addAll(get_utxo_util.getSpendableWithMempool(addr).values());
    return lst;

  }
}
