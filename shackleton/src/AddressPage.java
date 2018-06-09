package snowblossom.shackleton;

import com.google.protobuf.ByteString;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.trie.proto.TrieNode;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class AddressPage
{
  private final PrintStream out;
  private final AddressSpecHash address;
  private final Shackleton shackleton;
  private final DecimalFormat df = new DecimalFormat("0.000000");

  public AddressPage(PrintStream out, AddressSpecHash address, Shackleton shackleton)
  {
    this.out = out;
    this.address = address;
    this.shackleton = shackleton;
  }

  public void render()
  {
    out.println("<p>Address: " + AddressUtil.getAddressString(shackleton.getParams().getAddressPrefix(), address) + "</p>");
    loadData();

    double val_conf_d = (double) valueConfirmed / (double) Globals.SNOW_VALUE;
    double val_unconf_d = (double) valueUnconfirmed / (double) Globals.SNOW_VALUE;
    out.println(String.format("<p>%s (%s pending) in %d outputs</p>", df.format(val_conf_d), df.format(val_unconf_d), totalOutputs));

    out.println("<table>");
    out.println(" <thead>");
    out.println("   <th></th>");
    out.println(" </thead>");
    out.println(" <tbody>");
    for (TransactionBridge bridge : bridges)
    {
      renderTransactionBridge(bridge);
      //out.println(bridge.in.getSrcTxId()
    }
    out.println(" </tbody>");
    out.println("</table>");

  }

  private void renderTransactionBridge(TransactionBridge bridge)
  {
    out.println("<tr>");

    out.println("<td>");
    out.println(bridge.in.getSrcTxId())
    Block blk = shackleton.getStub().getBlock( RequestBlock.newBuilder().setBlockHash(bridge.in.getSrcTxId()).build());
    out.println(
    out.println("</td>");

    out.println("</tr>");
  }

  long valueConfirmed = 0;
  long valueUnconfirmed = 0;
  long totalSpendable = 0;
  long totalOutputs;
  List<TransactionBridge> bridges;
  List<Transaction> transactions = new ArrayList<Transaction>();

  private void loadData()
  {

    try
    {
      bridges = getSpendable(address);
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

  public List<TransactionBridge> getSpendable(AddressSpecHash addr)
  {

    // first get all the unpent outputs and and put them into a hashmap by input transaction hash.
    GetUTXONodeReply reply = shackleton.getStub().getUTXONode(GetUTXONodeRequest.newBuilder().setPrefix(addr.getBytes()).setIncludeProof(true).build());
    HashMap<String, TransactionBridge> bridge_map = new HashMap<>();
    for (TrieNode node : reply.getAnswerList())
    {
      if (node.getIsLeaf())
      {
        // get transaction id:
        ByteString key = node.getPrefix();
        ByteBuffer bb = ByteBuffer.wrap(key.toByteArray());
        byte[] address_hash = new byte[Globals.ADDRESS_SPEC_HASH_LEN];
        byte[] txid = new byte[Globals.BLOCKCHAIN_HASH_LEN];

        Transaction tx = shackleton.getStub().getTransaction( RequestTransaction.newBuilder().setTxHash(ByteString.copyFrom(txid)).build());
        if (tx.getInnerData().size() > 0)
        {
          transactions.add(tx);
        }

        TransactionBridge b = new TransactionBridge(node);
        bridge_map.put(b.getKeyString(), b);
      }
    }

    // get all the transactions in the mempool for this address
    for (ByteString tx_hash : shackleton.getStub().getMempoolTransactionList(RequestAddress.newBuilder().setAddressSpecHash(addr.getBytes()).build()).getTxHashesList())
    {
      Transaction tx = shackleton.getStub().getTransaction(RequestTransaction.newBuilder().setTxHash(tx_hash).build());
      TransactionInner inner = TransactionUtil.getInner(tx);

      // if it is from this address:
      for (TransactionInput in : inner.getInputsList())
      {
        if (addr.equals(in.getSpecHash()))
        {
          TransactionBridge b_in = new TransactionBridge(in);
          String key = b_in.getKeyString();
          if (bridge_map.containsKey(key))
          {
            // spending a known utxo, mark it as spent
            bridge_map.get(key).spent = true;
          }
          else
          {
            // we haven't seen the utxo this this transaction is spending, so save it in the map for later.
            // this is really weird...
            bridge_map.put(key, b_in);
          }
        }
      }
      // if it is to this address.
      for (int o = 0; o < inner.getOutputsCount(); o++)
      {
        TransactionOutput out = inner.getOutputs(o);
        if (addr.equals(out.getRecipientSpecHash()))
        {
          TransactionBridge b_out = new TransactionBridge(out, o, new ChainHash(tx_hash));
          String key = b_out.getKeyString();
          b_out.unconfirmed = true;

          // we've seen a bridge with the same key and marked it as spent, so mark this as spent and replace it in the map.
          // again, weird...
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
