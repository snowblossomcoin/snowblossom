package snowblossom.iceleaf;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

import snowblossom.lib.Globals;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.NetworkParamsProd;
import java.util.prefs.Preferences;
import snowblossom.iceleaf.components.*;
import java.io.File;


import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;



public class IceLeaf
{
  public static void main(String args[]) throws Exception
  {
    System.out.println(System.getProperty("user.home"));
    Globals.addCryptoProvider();
    new IceLeaf();

  }

  protected Preferences ice_leaf_prefs;
  protected NodePanel node_panel;
  protected NodeSelectionPanel node_select_panel;
  protected WalletPanel wallet_panel;
  protected MakeWalletPanel make_wallet_panel;

  public Preferences getPrefs() { return ice_leaf_prefs;}
  public NetworkParams getParams() { return new NetworkParamsProd(); }
  public UserServiceBlockingStub getStub(){return node_select_panel.getStub();}
  public UserServiceStub getAsyncStub(){return node_select_panel.getAsyncStub();}

  public IceLeaf()
  {

    ice_leaf_prefs = Preferences.userNodeForPackage(this.getClass());

    node_panel = new NodePanel(this);
    node_select_panel = new NodeSelectionPanel(this);
    wallet_panel = new WalletPanel(this);
    make_wallet_panel = new MakeWalletPanel(this);

    SwingUtilities.invokeLater(new WindowSetup());

  }

  public class WindowSetup implements Runnable
  {
    public void run()
    {
      JFrame f=new JFrame();
      f.setVisible(true);
      f.setDefaultCloseOperation( f.EXIT_ON_CLOSE);
      f.setTitle("SnowBlossom - IceLeaf " + Globals.VERSION);
      f.setSize(800, 600);

      JTabbedPane tab_pane = new JTabbedPane();

      f.setContentPane(tab_pane);
      node_panel.setup();
      node_select_panel.setup();
      wallet_panel.setup();
      make_wallet_panel.setup();

      JPanel settings_panel = assembleSettingsPanel();


      tab_pane.add("Wallets", wallet_panel.getPanel());
      tab_pane.add("Make Wallet", make_wallet_panel.getPanel());
      tab_pane.add("Node Selection", node_select_panel.getPanel());
      tab_pane.add("Node", node_panel.getPanel());
      tab_pane.add("Settings", settings_panel);

    }

    public JPanel assembleSettingsPanel()
    {
      GridBagLayout grid_bag = new GridBagLayout();
      JPanel panel = new JPanel(grid_bag);

      {
        GridBagConstraints c = new GridBagConstraints();
        c.weightx = 0.0;
        c.weighty= 0.0;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.NORTHWEST;

        c.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(new PersistentComponentCheckBox(ice_leaf_prefs, "Run local node", "node_run_local", false), c);
        
        c.gridwidth = 1;
        panel.add(new JLabel("Service Port"), c);
        c.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(new PersistentComponentTextField(ice_leaf_prefs, "", "node_service_port", "2338",8),c);

        c.gridwidth = 1;
        panel.add(new JLabel("TLS Service Port"), c);
        c.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(new PersistentComponentTextField(ice_leaf_prefs, "", "node_tls_service_port", "2348",8),c);


        c.gridwidth = 1;
        panel.add(new JLabel("Node DB Directory"), c);
        c.gridwidth = GridBagConstraints.REMAINDER;
        File default_node_db_path = new File(SystemUtil.getNodeDataDirectory(), "node_db");
        panel.add(new PersistentComponentTextField(ice_leaf_prefs, "", "node_db_path", default_node_db_path.toString(),60),c);

        c.gridwidth = 1;
        panel.add(new JLabel("Node TLS Key Directory"), c);
        c.gridwidth = GridBagConstraints.REMAINDER;
        File default_node_tls_key_path = new File(SystemUtil.getNodeDataDirectory(), "node_tls_key");
        panel.add(new PersistentComponentTextField(ice_leaf_prefs, "", "node_tls_key_path", default_node_tls_key_path.toString(),60),c);

        c.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(new PersistentComponentCheckBox(ice_leaf_prefs, "Node transaction index", "node_tx_index", true), c);
        panel.add(new PersistentComponentCheckBox(ice_leaf_prefs, "Node address index", "node_addr_index", true), c);
      }

      return panel;

    }

  }

}
