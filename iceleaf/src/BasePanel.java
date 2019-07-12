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
import java.awt.Font;

public abstract class BasePanel
{
  protected JScrollPane outer_panel;
  protected JPanel panel;
	protected Preferences ice_leaf_prefs;
  protected IceLeaf ice_leaf;

  protected JTextArea status_box;
  protected JTextArea message_box;


  public BasePanel(IceLeaf ice_leaf)
  {
    this.ice_leaf = ice_leaf;
		ice_leaf_prefs = ice_leaf.getPrefs();
		GridBagLayout grid_bag = new GridBagLayout();
		panel = new JPanel(grid_bag);
    outer_panel = new JScrollPane(panel);
    
    panel.setBackground(ice_leaf.getBGColor());
		status_box = new JTextArea();
		message_box = new JTextArea();

    status_box.setFont(new Font("Hack", 0, 12));
    message_box.setFont(new Font("Hack", 0, 12));

	}

	public abstract void setupPanel();

  public void setup()
  {
  	setupPanel(); 

    GridBagConstraints c = new GridBagConstraints();
    c.gridheight = 1;
		c.gridwidth = GridBagConstraints.REMAINDER;
    c.anchor = GridBagConstraints.NORTHWEST;

    c.weightx=0.0;
    c.weighty=0.0;

	  panel.add(new JLabel("Status:"),c);	

    status_box.setEditable(false);
    panel.add(status_box,c);
	  
    panel.add(new JLabel("Message:"),c);	

    message_box.setEditable(false);
    panel.add(message_box,c);
 
    c.anchor = GridBagConstraints.SOUTHEAST;
    c.weightx=1.0;
    c.weighty=1.0;
    panel.add(new JLabel("Snowblossom"),c);	
  }

  public JComponent getPanel(){return outer_panel;}

  public void setMessageBox(String text)
  {
    SwingUtilities.invokeLater(new Runnable() {
      public void run()
      {
        message_box.setText(text);
      }
    });
  }

	public void setStatusBox(String text)
  {
    SwingUtilities.invokeLater(new Runnable() {
      public void run()
      {
        status_box.setText(text);
      }
    });
  }


}
