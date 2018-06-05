package snowblossom.gclient.views;

import com.google.protobuf.ByteString;
import duckutil.ConfigFile;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.trie.proto.TrieNode;
import wallet.src.WalletUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;


public class AddressListView extends JPanel
{
  private JScrollPane scroller;
  private JList<AddressInfo> list;
  private AddressListModel listModel;
  private File walletFile;
  private ConfigFile config;
  private UserServiceGrpc.UserServiceStub asyncStub;
  private UserServiceGrpc.UserServiceBlockingStub blockingStub;

  public AddressListView(File walletFile, ConfigFile config)
  {
    this.config = config;
    this.walletFile = walletFile;
    list = new JList<>();
    scroller = new JScrollPane(list);
    this.add(scroller);
    listModel = new AddressListModel();
    list.setModel(listModel);
    //refreshAddresses();
    list.setCellRenderer(new AddressInfoCellRender());
  }

  public void refreshAddresses()
  {
    System.out.println("refreshing addresses");
    String host = config.get("node_host");
    NetworkParams params = NetworkParams.loadFromConfig(config);
    int port = config.getIntWithDefault("node_port", params.getDefaultPort());
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();

    asyncStub = UserServiceGrpc.newStub(channel);
    blockingStub = UserServiceGrpc.newBlockingStub(channel);

    long total_confirmed = 0;
    long total_unconfirmed = 0;
    long total_spendable = 0;
    DecimalFormat df = new DecimalFormat("0.000000");
    WalletDatabase wallet;
    System.out.println("loading wallet: " + walletFile);
    try
    {
      wallet = WalletUtil.loadWallet(walletFile);
      if (wallet == null)
      {
        wallet = WalletUtil.makeWallet(walletFile, 8, "standard");
      }
    }
    catch (Exception e)
    {
      throw new RuntimeException(e);
    }

    Throwable logException = null;
    List<AddressInfo> infos = new ArrayList<>();
    for (AddressSpec claim : wallet.getAddressesList())
    {
      AddressSpecHash hash = AddressUtil.getHashForSpec(claim);
      String address = AddressUtil.getAddressString(params.getAddressPrefix(), hash);
      long value_confirmed = 0;
      long value_unconfirmed = 0;
      try
      {
        List<TransactionBridge> bridges = getSpendable(hash);

        for (TransactionBridge b : bridges)
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

        AddressInfo info = new AddressInfo();
        info.address = address;
        info.outputs = bridges.size();
        info.pending = value_unconfirmed;
        info.spendable = value_confirmed;
        System.out.println("adding info: " + info.address);
        infos.add(info);

        total_confirmed += value_confirmed;
        total_unconfirmed += value_unconfirmed;
      }
      catch (Throwable e)
      {
        logException = e;
        System.out.println(e);
      }

    }
    if (logException != null)
    {
      System.out.println("Last exception stacktrace:");
      logException.printStackTrace(System.out);
    }
    listModel.setItems(infos);

    double total_conf_d = (double) total_confirmed / (double) Globals.SNOW_VALUE;
    double total_unconf_d = (double) total_unconfirmed / (double) Globals.SNOW_VALUE;
    double total_spend_d = (double) total_spendable / Globals.SNOW_VALUE_D;
    System.out.println(String.format("Total: %s (%s pending) (%s spendable)", df.format(total_conf_d), df.format(total_unconf_d),
                                     df.format(total_spend_d)));
  }

  public List<TransactionBridge> getSpendable(AddressSpecHash addr)
  {

    GetUTXONodeReply reply = blockingStub.getUTXONode(GetUTXONodeRequest.newBuilder()
                                                                        .setPrefix(addr.getBytes())
                                                                        .setIncludeProof(true)
                                                                        .build());

    HashMap<String, TransactionBridge> bridge_map = new HashMap<>();

    for (TrieNode node : reply.getAnswerList())
    {
      if (node.getIsLeaf())
      {
        TransactionBridge b = new TransactionBridge(node);

        bridge_map.put(b.getKeyString(), b);
      }
    }

    for (ByteString tx_hash : blockingStub.getMempoolTransactionList(
      RequestAddress.newBuilder().setAddressSpecHash(addr.getBytes()).build()).getTxHashesList())
    {
      Transaction tx = blockingStub.getTransaction(RequestTransaction.newBuilder().setTxHash(tx_hash).build());

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

  public class AddressListModel extends DefaultListModel<AddressInfo>
  {
    public void setItems(List<AddressInfo> addresses)
    {
      HashMap<String, AddressInfo> map = new HashMap<>();
      for (AddressInfo a : addresses)
      {
        map.put(a.address, a);
      }
      for (int i = 0; i < getSize(); i++)
      {
        AddressInfo a = get(i);
        AddressInfo newVal = map.get(a.address);
        if (newVal != null)
        {
          a.spendable = newVal.spendable;
          a.pending = newVal.pending;
          a.outputs = newVal.outputs;
          fireContentsChanged(this, i, i);
          map.remove(a.address);
        }
        else
        {
          remove(i);
          i--;
        }
      }
      for (AddressInfo a : map.values())
      {
        System.out.println("adding to model: " + a);
        addElement(a);
      }
    }
  }

  public class AddressInfo
  {
    public String address;
    public long spendable, pending, outputs;

    @Override
    public String toString()
    {
      return address + " - " + ((double)spendable / Globals.SNOW_VALUE) + " - " + outputs + " outputs";
    }
  }

  public class AddressInfoCellRender extends DefaultListCellRenderer
  {

    @Override
    public Component getListCellRendererComponent(JList list, Object value,
                                                  int index, boolean isSelected, boolean cellHasFocus) {
      System.out.println("rendering: " + value);
      JLabel renderer = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value != null) {
        renderer.setText(String.valueOf(value));
      }
      return renderer;
    }
  }
}
