package snowblossom.iceleaf;

import com.google.common.collect.ImmutableList;
import duckutil.Config;
import duckutil.ConfigCat;
import duckutil.ConfigFile;
import duckutil.ConfigMem;
import duckutil.PeriodicThread;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JButton;
import javax.swing.JLabel;
import snowblossom.client.SeedReport;
import snowblossom.client.SnowBlossomClient;
import snowblossom.client.WalletUtil;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;
import snowblossom.proto.WalletDatabase;

public class WalletPanel extends BasePanel
{
  protected WalletUpdateThread update_thread;

  protected TreeMap<String, SnowBlossomClient> client_map = new TreeMap<>();
  protected LinkedList<PeriodicThread> wake_threads = new LinkedList<>(); 

  protected WalletComboBox wallet_select_box;
  protected JButton details_button;

  // Don't overwrite the message until this time,
  // so user can get any message there from details
  protected long message_lockout = 0;

  public WalletPanel(IceLeaf ice_leaf)
  {
    super(ice_leaf);
	}

  @Override
	public void setupPanel()
	{

    GridBagConstraints c = new GridBagConstraints();
    c.weightx = 0.0;
    c.weighty = 0.0;
    c.gridheight = 1;
    c.anchor = GridBagConstraints.WEST;

    c.gridwidth = 1;
    panel.add(new JLabel("Wallet details:"), c);

    
    wallet_select_box = new WalletComboBox(ice_leaf);
    panel.add(wallet_select_box, c);
    
    details_button = new JButton("Details");


    c.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(details_button, c);

    details_button.addActionListener( new DetailsButtonListener());


    update_thread = new WalletUpdateThread();
    update_thread.start();
  }

  public void wake()
  {
    if (update_thread != null)
    {
      update_thread.wake();
    }
  }

  public class DetailsButtonListener implements ActionListener
  {
    public void actionPerformed(ActionEvent e)
    {
      StringBuilder sb = new StringBuilder();

			String wallet_str = (String)wallet_select_box.getSelectedItem();

      sb.append("Detail for " + wallet_str);
			ByteArrayOutputStream b_out = new ByteArrayOutputStream();
			PrintStream p_out = new PrintStream(b_out);
      

      SnowBlossomClient client = ice_leaf.getWalletPanel().getWallet(wallet_str);
      if (client != null)
      {
        p_out.println();

        WalletDatabase db = client.getPurse().getDB();
        SeedReport sr = WalletUtil.getSeedReport(db);

        for(Map.Entry<String, String> seed : sr.seeds.entrySet())
        { 
          p_out.println("Public: " + seed.getValue());
          p_out.println("Seed: " + seed.getKey());
        }  
        for(String xpub : sr.watch_xpubs)
        {
          p_out.println("Watch-only xpub: " + xpub);
        }

        if (sr.watch_xpubs.size() == 0)
        {
          if (sr.missing_keys > 0)
          {   
              p_out.println(
                String.format("WARNING: THIS WALLET CONTAINS %d KEYS THAT DO NOT COME FROM SEEDS.  THIS WALLET CAN NOT BE COMPLETELY RESTORED FROM SEEDS", sr.missing_keys));
          }
          else
          { 
            p_out.println("All keys in this wallet are derived from the seed(s) above and will be recoverable from those seeds.");
          }
        }
      }

			sb.append(new String(b_out.toByteArray()));

      message_lockout = System.currentTimeMillis() + 60000L;
      setMessageBox(sb.toString());
    }

  }
  

  public class WalletUpdateThread extends PeriodicThread
  {
    boolean first_pass = true;
    public WalletUpdateThread()
    {
      super(15000);
    }

    public void runPass() throws Exception
    {
      if (System.currentTimeMillis() < message_lockout) return;

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
            sb.append("Wallet: " + getWalletSummary(name, db_dir, config_file, first_pass) +"\n\n");
          }
        }

        setWalletBox(sb.toString().trim());
        setMessageBox("");
        synchronized(wake_threads)
        {
          for(PeriodicThread p : wake_threads) p.wake();
        }
        first_pass = false;

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

  private String getWalletSummary(String name, File db_dir, File config_file, boolean first_pass)
    throws Exception
  {
    SnowBlossomClient client = loadWallet(name, db_dir, config_file);

    if (!first_pass)
    {
      setMessageBox("Maintaining: " + name);
      client.maintainKeys();
    }

    setMessageBox("Fresh address: " + name);
    AddressSpecHash hash  = client.getPurse().getUnusedAddress(false, false);
    String addr = AddressUtil.getAddressString(client.getParams().getAddressPrefix(), hash);

    setMessageBox("Checking balance: " + name);

    String summary = String.format("%s - %s\n    %s", 
      name, 
      SnowBlossomClient.getBalanceInfoPrint(client.getBalance()),
      addr
      );

    if (client.getPurse().isWatchOnly())
    {
      summary = summary +"\n    WATCH ONLY";
    }
    return summary;
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
    setStatusBox(text);
  }


}
