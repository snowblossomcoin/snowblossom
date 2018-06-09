package snowblossom.shackleton;

import com.google.protobuf.ByteString;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.trie.proto.TrieNode;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

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

  private String formatFlakeValue(long value)
  {
    double v = (double) value / (double) Globals.SNOW_VALUE;
    return String.format("%s", df.format(v));
  }

  public void render()
  {
    out.println("<p>Address: " + AddressUtil.getAddressString(shackleton.getParams().getAddressPrefix(), address) + "</p>");
    AddressData data = getAddressData(address);

    double val_conf_d = (double) data.valueConfirmed / (double) Globals.SNOW_VALUE;
    double val_unconf_d = (double) data.valueUnconfirmed / (double) Globals.SNOW_VALUE;
    out.println(String.format("<p>%s (%s pending) in %d outputs</p>", df.format(val_conf_d), df.format(val_unconf_d), data.totalOutputs));

    out.println("<h2>Unspend Transaction Outputs</h2>");
    out.println("(does not include unconfirmed)");
    out.println("<table>");
    out.println(" <thead>");
    out.println("   <th></th>");
    out.println(" </thead>");
    out.println(" <tbody>");
    for (TransactionAndUtxos tAndU : data.transactionsAndUtxos)
    {
      renderTransactionAndUtxos(tAndU);
    }
    out.println(" </tbody>");
    out.println("</table>");

  }

  private void renderTransactionAndUtxos(TransactionAndUtxos t)
  {
    ChainHash tx_hash = new ChainHash(t.transaction.getTxHash());
    TransactionInner inner = TransactionUtil.getInner(t.transaction);

    BlockHeader blk = null;
    if (inner.hasCoinbaseExtras())
    {
      int blockHeight = inner.getCoinbaseExtras().getBlockHeight();
      blk = shackleton.getStub().getBlockHeader(RequestBlockHeader.newBuilder().setBlockHeight(blockHeight).build());
    }


    out.println("<tr>");

    out.println("<td>");
    out.println(tx_hash.toString());
    out.println("</td>");

    out.println("<td>");
    if (blk != null)
    {
      out.println(inner.getIsCoinbase());
    }
    out.println("</td>");

    out.println("<td>");
    if (blk != null)
    {
      SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
      sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
      Date resultDate = new Date(blk.getTimestamp());
      out.println(sdf.format(resultDate));
    }
    out.println("</td>");

    out.println("<td>");
    if (blk != null)
    {
      out.println(blk.getBlockHeight());
    }
    out.println("</td>");

    out.println("<td>");
    if (blk != null)
    {
      for (TransactionBridge bridge : t.utxos)
      {
        out.println("<div>");
        out.println(formatFlakeValue(bridge.value));
        if (bridge.spent) out.println(" (spent)");
        out.println("</div>");
      }
      out.println(blk.getBlockHeight());
    }
    out.println("</td>");

    out.println("</tr>");
  }

  private class AddressData
  {
    private List<TransactionAndUtxos> transactionsAndUtxos;
    private List<TransactionBridge> spendable;

    private AddressData(List<TransactionAndUtxos> transactionsAndUtxos, List<TransactionBridge> spendable)
    {
      this.transactionsAndUtxos = transactionsAndUtxos;
      this.spendable = spendable;
    }

    long valueConfirmed = 0;
    long valueUnconfirmed = 0;
    long totalSpendable = 0;
    long totalOutputs = 0;

    private void calculateValues()
    {
      for (TransactionBridge b : spendable)
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
  }

  private class TransactionAndUtxos
  {
    private final Transaction transaction;
    private final List<TransactionBridge> utxos;

    private TransactionAndUtxos(Transaction transaction)
    {
      this.transaction = transaction;
      utxos = new ArrayList<>();
    }
  }

  private AddressData getAddressData(AddressSpecHash addr)
  {
    // first get all the unpent outputs and and put them into a hashmap by input transaction hash.
    // also save them into the transaction and utxo list.
    GetUTXONodeReply reply = shackleton.getStub().getUTXONode(GetUTXONodeRequest.newBuilder().setPrefix(addr.getBytes()).setIncludeProof(true).build());
    HashMap<String, TransactionBridge> bridge_map = new HashMap<>();
    HashMap<String, TransactionAndUtxos> transaction_map = new HashMap<>();
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
        TransactionAndUtxos tAndU;
        if (!transaction_map.containsKey(tx.getTxHash().toString()))
        {
          tAndU = new TransactionAndUtxos(tx);
          transaction_map.put(tx.getTxHash().toString(), tAndU);
        }
        else
        {
          tAndU = transaction_map.get(tx.getTxHash().toString());
        }
        TransactionBridge b = new TransactionBridge(node);
        tAndU.utxos.add(b);
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

    LinkedList<TransactionAndUtxos> lst2 = new LinkedList<>();
    lst2.addAll(transaction_map.values());

    AddressData ad = new AddressData(lst2, lst);
    ad.calculateValues();
    return ad;
  }
}
