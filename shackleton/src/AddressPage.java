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

public class AddressPage
{
  private final PrintStream out;
  private final AddressSpecHash address;
  private final NetworkParams params;
  private final UserServiceBlockingStub stub;
  private final DecimalFormat df = new DecimalFormat("0.000000");
  private final boolean want_history;

  public AddressPage(PrintStream out, AddressSpecHash address, NetworkParams params, UserServiceBlockingStub stub, boolean want_history)
  {
    this.out = out;
    this.address = address;
    this.params = params;
    this.stub = stub;
    this.want_history = want_history;
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

  protected void loadData()
  {

    try
    {
      bridges = getSpendable(address);
      if (want_history)
      {
        history = getHistory(address);
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
  {

    GetUTXONodeReply reply = stub.getUTXONode(GetUTXONodeRequest.newBuilder().setPrefix(addr.getBytes()).setIncludeProof(true).build());

    HashMap<String, TransactionBridge> bridge_map = new HashMap<>();

    for (TrieNode node : reply.getAnswerList())
    {
      if (node.getIsLeaf())
      {
        TransactionBridge b = new TransactionBridge(node);

        bridge_map.put(b.getKeyString(), b);
      }
    }

    for (ByteString tx_hash : stub.getMempoolTransactionList(RequestAddress.newBuilder().setAddressSpecHash(addr.getBytes()).build()).getTxHashesList())
    {
      Transaction tx = stub.getTransaction(RequestTransaction.newBuilder().setTxHash(tx_hash).build());
      TransactionInner inner = TransactionUtil.getInner(tx);

      for (TransactionInput in : inner.getInputsList())
      {
        if (addr.equals(in.getSpecHash()))
        {
          TransactionBridge b_in = new TransactionBridge(in);
          String key = b_in.getKeyString();
          if (bridge_map.containsKey(key))
          {
            bridge_map.get(key).spent = true;
          }
          else
          {
            bridge_map.put(key, b_in);
          }
        }
      }
      for (int o = 0; o < inner.getOutputsCount(); o++)
      {
        TransactionOutput out = inner.getOutputs(o);
        if (addr.equals(out.getRecipientSpecHash()))
        {
          TransactionBridge b_out = new TransactionBridge(out, o, new ChainHash(tx_hash));
          String key = b_out.getKeyString();
          b_out.unconfirmed = true;

          if (bridge_map.containsKey(key))
          {
            if (bridge_map.get(key).spent)
            {
              b_out.spent = true;
            }
          }
          bridge_map.put(key, b_out);
        }
      }
    }
    LinkedList<TransactionBridge> lst = new LinkedList<>();
    lst.addAll(bridge_map.values());
    return lst;

  }
}
