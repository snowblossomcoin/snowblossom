package snowblossom.iceleaf;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.util.prefs.Preferences;
import javax.swing.JComboBox;

import snowblossom.client.SnowBlossomClient;
import snowblossom.lib.Globals;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;

import duckutil.PeriodicThread;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Collection;


public class SendPanel
{
  private JPanel panel;
	protected Preferences ice_leaf_prefs;
  protected IceLeaf ice_leaf;

  protected JTextArea message_box;
  protected SendThread send_thread;

  protected JComboBox wallet_select_box;
  protected TreeSet<String> current_select_box_items=new TreeSet<>();


  public SendPanel(IceLeaf ice_leaf)
  {
    this.ice_leaf = ice_leaf;
		ice_leaf_prefs = ice_leaf.getPrefs();
	}

	public void setup()
	{
		GridBagLayout grid_bag = new GridBagLayout();
		panel = new JPanel(grid_bag);

    GridBagConstraints c = new GridBagConstraints();
    c.weightx = 0.0;
    c.weighty= 0.0;
    c.gridheight = 1;
    c.anchor = GridBagConstraints.NORTHWEST;


    c.gridwidth = 1;
    panel.add(new JLabel("Wallet to send from:"), c);
    c.gridwidth = GridBagConstraints.REMAINDER;

    wallet_select_box = new WalletComboBox(ice_leaf);
    panel.add(wallet_select_box, c);



    c.weightx=1.0;
    c.weighty=1.0;
 
    message_box = new JTextArea();
    message_box.setEditable(false);
    panel.add(message_box,c);
    send_thread = new SendThread();
    send_thread.start();

  }

  public JPanel getPanel()
  {
		return panel;
  }

  public void wake()
  {
    if (send_thread != null)
    {
      send_thread.wake();
    }
  }
  

  public class SendThread extends PeriodicThread
  {
    public SendThread()
    {
      super(15000);
    }

    public void runPass() throws Exception
    {
      try
      {

      }
      catch(Throwable e)
      {
        String text = ErrorUtil.getThrowInfo(e);
        setMessageBox(text);
       
      }

    }

  }

  public void setMessageBox(String text)
  {
    SwingUtilities.invokeLater(new Runnable() {
      public void run()
      {
        message_box.setText(text);
      }
    });
  }

}
