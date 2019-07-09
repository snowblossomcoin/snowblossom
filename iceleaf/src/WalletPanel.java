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

import snowblossom.client.SnowBlossomClient;
import snowblossom.node.SnowBlossomNode;
import snowblossom.lib.Globals;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;

import duckutil.PeriodicThread;
import duckutil.ConfigMem;
import duckutil.ConfigFile;
import duckutil.ConfigCat;
import duckutil.Config;
import java.util.TreeMap;
import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import com.google.common.collect.ImmutableList;


public class WalletPanel
{
  private JPanel panel;
	protected Preferences ice_leaf_prefs;
  protected IceLeaf ice_leaf;

  protected JTextArea wallet_box;
  protected JTextArea message_box;
  protected WalletUpdateThread update_thread;

  protected TreeMap<String, SnowBlossomClient> client_map = new TreeMap<>();
  protected LinkedList<PeriodicThread> wake_threads = new LinkedList<>(); 


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
 
    wallet_box = new JTextArea();
    wallet_box.setEditable(false);
    panel.add(wallet_box,c);

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
    if (update_thread != null)
    {
      update_thread.wake();
    }
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
        for(int i=0;i<25; i++)
        {
          if (ice_leaf.getStubHolder().getBlockingStub()==null)
          {
            setMessageBox("Waiting for open channel");
            Thread.sleep(100);
          }
        }
        setMessageBox("Loading wallets");
        if (ice_leaf.getStubHolder().getBlockingStub()==null)
        {
          throw new Exception("Channel not yet open");
        }
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
            sb.append("Wallet: " + getWalletSummary(name, db_dir, config_file) +"\n");
          }
        }

        setWalletBox(sb.toString());
        setMessageBox("");
        synchronized(wake_threads)
        {
          for(PeriodicThread p : wake_threads) p.wake();
        }

      }
      catch(Throwable e)
      {
        String text = ErrorUtil.getThrowInfo(e);
        setMessageBox(text);
       
      }

    }

  }

  public void addWakeThread(PeriodicThread p)
  {
    synchronized(wake_threads)
    {
      wake_threads.add(p);
    }

  }

  private String getWalletSummary(String name, File db_dir, File config_file)
    throws Exception
  {
    SnowBlossomClient client = loadWallet(name, db_dir, config_file);
    setMessageBox("Maintaining: " + name);
    client.maintainKeys();

    AddressSpecHash hash  = client.getPurse().getUnusedAddress(false, false);
    String addr = AddressUtil.getAddressString(client.getParams().getAddressPrefix(), hash);

    return String.format("%s - %s - %s", 
      name, 
      SnowBlossomClient.getBalanceInfoPrint(client.getBalance()),
      addr
      );
  }

  public Collection<String> getNames()
  {
    synchronized(client_map)
    {
      return ImmutableList.copyOf(client_map.keySet());
    }
  }

  public SnowBlossomClient getWallet(String name)
  {
    synchronized(client_map)
    {
      return client_map.get(name);
    }
  }

  private SnowBlossomClient loadWallet(String name, File db_dir, File config_file)
    throws Exception
  {
    synchronized(client_map)
    {
      if (client_map.containsKey(name)) return client_map.get(name);
    }
    TreeMap<String, String> config_map = new TreeMap<>();
    config_map.put("wallet_path", db_dir.getPath());
    config_map.put("network", ice_leaf.getParams().getNetworkName());
		Config conf = new ConfigCat(new ConfigMem(config_map), new ConfigFile(config_file.getPath()));

	  SnowBlossomClient client = new SnowBlossomClient(conf, null, ice_leaf.getStubHolder());

    synchronized(client_map)
    {
      client_map.put(name, client);
    }
    return client;



  }

  public void setWalletBox(String text)
  {
    SwingUtilities.invokeLater(new Runnable() {
      public void run()
      {
        wallet_box.setText(text);
      }
    });
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
