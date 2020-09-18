package snowblossom.iceleaf;

import duckutil.PeriodicThread;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import javax.swing.JLabel;
import snowblossom.client.SnowBlossomClient;
import snowblossom.client.WalletUtil;
import snowblossom.lib.AddressSpecHash;
import snowblossom.proto.BalanceInfo;
import snowblossom.proto.WalletDatabase;
import snowblossom.util.proto.*;

public class AddressPanel extends BasePanel
{
  protected WalletComboBox wallet_select_box;
  protected UpdateThread update_thread;

  public AddressPanel(IceLeaf ice_leaf)
  {
    super(ice_leaf);
  }

  @Override
  public void setupPanel()
  {
    
    GridBagConstraints c = new GridBagConstraints();
    c.weightx = 0.0;
    c.weighty= 0.0;
    c.gridheight = 1;
    c.anchor = GridBagConstraints.WEST;

    c.gridwidth = 1;
    panel.add(new JLabel("Wallet to view addresses of:"), c);
    c.gridwidth = GridBagConstraints.REMAINDER;

    wallet_select_box = new WalletComboBox(ice_leaf);
    panel.add(wallet_select_box, c);
 
    update_thread = new UpdateThread();
    update_thread.start();
    
    wallet_select_box.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e)
      {
        update_thread.wake();
      }
    });

  }

  public class UpdateThread extends PeriodicThread
  {
    public UpdateThread()
    {
      super(45000);

    }
    public void runPass()
    {
      try
      {
        String wallet_name = (String)wallet_select_box.getSelectedItem();
        if (wallet_name == null)
        {
          setMessageBox("no wallet selected");
          setStatusBox("");
          return;
        }
        
        SnowBlossomClient client = ice_leaf.getWalletPanel().getWallet( wallet_name );
        if (client == null)
        {
          setMessageBox("no wallet selected");
          setStatusBox("");
          return;
        }
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintStream pout = new PrintStream(bout);
        pout.println(String.format("Wallet: %s", wallet_name));

        WalletDatabase db = client.getPurse().getDB();

        for(AddressSpecHash hash : WalletUtil.getAddressesByAge(db, ice_leaf.getParams()))
        {
          String address = hash.toAddressString(ice_leaf.getParams());
          boolean used = db.getUsedAddressesMap().containsKey(address);
          BalanceInfo bi = client.getBalance(hash);
          String bi_print = SnowBlossomClient.getBalanceInfoPrint(bi);

          pout.println(String.format(" %s - used:%s - %s", address, ""+used, bi_print));

        }

        setStatusBox(new String(bout.toByteArray()).trim());
        setMessageBox("");

      }
      catch(Throwable t)
      {
        setMessageBox(ErrorUtil.getThrowInfo(t));
        setStatusBox("");
      }
    }

  }

}
