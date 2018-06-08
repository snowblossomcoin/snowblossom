package snowblossom.shackleton;

import com.google.protobuf.ByteString;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.trie.proto.TrieNode;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class AddressPage
{
  private final PrintStream out;
  private final AddressSpecHash address;
  private final Shackleton shackleton;

  public AddressPage(PrintStream out, AddressSpecHash address, Shackleton shackleton)
  {
    this.out = out;
    this.address = address;
    this.shackleton = shackleton;
  }

  public void render()
  {

  }

  long loadConfirmed = 0;
  long valueUnconfirmed = 0;

  private void loadData()
  {

    try
    {
      List<TransactionBridge> bridges = getSpendable(hash);

      for(TransactionBridge b : bridges)
      {

        if (b.unconfirmed)
        {
          if (!b.spent)
          {
            value_unconfirmed += b.value;
          }
        }
        else //confirmed
        {
          value_confirmed += b.value;
          if (b.spent)
          {
            value_unconfirmed -= b.value;
          }
        }
        if (!b.spent)
        {
          total_spendable += b.value;
        }

      }

      double val_conf_d = (double) value_confirmed / (double) Globals.SNOW_VALUE;
      double val_unconf_d = (double) value_unconfirmed / (double) Globals.SNOW_VALUE;
      System.out.println(String.format(" %s (%s pending) in %d outputs",
                                       df.format(val_conf_d), df.format(val_unconf_d), bridges.size()));

      total_confirmed += value_confirmed;
      total_unconfirmed += value_unconfirmed;
    }
    catch(Throwable e)
    {
      logException = e;
      System.out.println(e);
    }
  }

  public List<TransactionBridge> getSpendable(AddressSpecHash addr)
  {

    GetUTXONodeReply reply = shackleton.getStub().getUTXONode(GetUTXONodeRequest.newBuilder()
                                                                        .setPrefix(addr.getBytes())
                                                                        .setIncludeProof(true)
                                                                        .build());

    HashMap<String, TransactionBridge> bridge_map=new HashMap<>();

    for(TrieNode node : reply.getAnswerList())
    {
      if (node.getIsLeaf())
      {
        TransactionBridge b = new TransactionBridge(node);

        bridge_map.put(b.getKeyString(), b);
      }
    }

    for(ByteString tx_hash : blockingStub.getMempoolTransactionList(
      RequestAddress.newBuilder().setAddressSpecHash(addr.getBytes()).build()).getTxHashesList())
    {
      Transaction tx = blockingStub.getTransaction(RequestTransaction.newBuilder().setTxHash(tx_hash).build());

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


    LinkedList<TransactionBridge> lst = new LinkedList<>();
    lst.addAll(bridge_map.values());
    return lst;

  }
}
