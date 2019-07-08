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

import snowblossom.node.SnowBlossomNode;
import snowblossom.lib.Globals;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;

import duckutil.PeriodicThread;
import duckutil.ConfigMem;
import java.util.TreeMap;
import java.io.File;

public class WalletPanel
{
  private JPanel panel;
	protected Preferences ice_leaf_prefs;
  protected IceLeaf ice_leaf;

  protected JTextArea message_box;
  protected WalletUpdateThread update_thread;


  public WalletPanel(IceLeaf ice_leaf)
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

    c.gridwidth = GridBagConstraints.REMAINDER;
    c.weightx=1.0;
    c.weighty=1.0;
 
    message_box = new JTextArea();
    message_box.setEditable(false);
    panel.add(message_box,c);
    update_thread = new WalletUpdateThread();
    update_thread.start();

  }

  public JPanel getPanel()
  {
		return panel;
  }

  public void wake()
  {
    update_thread.wake();
  }
  

  public class WalletUpdateThread extends PeriodicThread
  {
    public WalletUpdateThread()
    {
      super(15000);
    }

    public void runPass() throws Exception
    {
      try
      {
        String wallet_base_path = ice_leaf_prefs.get("wallet_path", null);
        if (wallet_base_path == null) throw new Exception("wallet_path is null");
        File base_file = new File(wallet_base_path);

        StringBuilder sb = new StringBuilder();

        if (base_file.exists() && base_file.isDirectory())
        for(File wallet_dir : base_file.listFiles())
        {
          File config_file = new File(wallet_dir, "wallet.conf");
          File db_dir = new File(wallet_dir, "db");
          String name = wallet_dir.getName();

          if (config_file.exists())
          if (config_file.isFile())
          if (db_dir.exists())
          if (db_dir.isDirectory())
          if (db_dir.list().length > 0)
          {
            sb.append("Wallet: " + name + "\n");
          }
        }

        setMessageBox(sb.toString());



      }
      catch(Throwable e)
      {
        String text = e.toString();
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
