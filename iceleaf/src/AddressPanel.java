package snowblossom.iceleaf;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.util.prefs.Preferences;
import javax.swing.JComboBox;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;
import javax.swing.JButton;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import snowblossom.client.SnowBlossomClient;
import snowblossom.lib.Globals;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;

import duckutil.PeriodicThread;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Collection;

import snowblossom.proto.SubmitReply;
import snowblossom.proto.Transaction;
import snowblossom.proto.TransactionInner;
import snowblossom.proto.TransactionOutput;
import snowblossom.util.proto.*;
import snowblossom.client.TransactionFactory;
import snowblossom.lib.TransactionUtil;
import snowblossom.lib.ChainHash;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;
import snowblossom.proto.AddressSpec;
import snowblossom.proto.WalletDatabase;
import snowblossom.proto.BalanceInfo;
import javax.swing.JScrollPane;
import javax.swing.JComponent;
import snowblossom.client.WalletUtil;

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
    c.anchor = GridBagConstraints.NORTHWEST;

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
      super(15000);

    }
    public void runPass()
    {
			try
			{
        String wallet_name = (String)wallet_select_box.getSelectedItem();
        if (wallet_name == null)
        {
          setMessageBox("no wallet selected");
          return;
        }
        
        SnowBlossomClient client = ice_leaf.getWalletPanel().getWallet( wallet_name );
        if (client == null)
        {
          setMessageBox("no wallet selected");
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
			}
    }

  }

}
