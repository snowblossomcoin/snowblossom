package snowblossom.iceleaf;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextArea;
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

public class NodePanel
{
  private JPanel panel;
	protected Preferences ice_leaf_prefs;
  protected SnowBlossomNode node;

  protected JTextArea message_box;
  protected JTextArea status_box;
  protected boolean start_attempt;


  public NodePanel(IceLeaf ice_leaf)
  {
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
		if (ice_leaf_prefs.getBoolean("node_run_local", false))
		{
			panel.add(new JLabel("Starting local node"), c);
      new NodeUpdateThread().start();

		}
		else
		{
			panel.add(new JLabel("Local node disabled"), c);

		}

    c.weightx=1.0;
    c.weighty=1.0;
    status_box = new JTextArea();
    status_box.setEditable(false);
    //status_box.setColumns(100);
    //status_box.setLineWrap(true);
    panel.add(status_box, c);
 
    message_box = new JTextArea();
    message_box.setEditable(false);
    //message_box.setColumns(100);
    //message_box.setLineWrap(true);
    panel.add(message_box,c);

    

  }

  public JPanel getPanel()
  {
		return panel;
  }

  public class NodeUpdateThread extends PeriodicThread
  {
    public NodeUpdateThread()
    {
      super(1000);
    }

    public void runPass() throws Exception
    {
      try
      {
        if (!start_attempt)
        {
          startNode();
        }
        StringBuilder sb=new StringBuilder();
        int net_height = 0;
        if ( node.getPeerage().getHighestSeenHeader() != null)
        {
          net_height = node.getPeerage().getHighestSeenHeader().getBlockHeight();
        }
        sb.append("Height: " + node.getBlockIngestor().getHeight() + " out of " + net_height +"\n");
        sb.append("Peers: " + node.getPeerage().getConnectedPeerCount() +"\n");
        sb.append("Node TLS key: " + AddressUtil.getAddressString(Globals.NODE_ADDRESS_STRING, node.getTlsAddress()) +"\n");

        setStatusBox(sb.toString());

      }
      catch(Exception e)
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

  public void setStatusBox(String text)
  {
    SwingUtilities.invokeLater(new Runnable() {
      public void run()
      {
        status_box.setText(text);
      }
    });
  }


  private void startNode()
    throws Exception
  {
    start_attempt=true;
    TreeMap<String, String> config_map = new TreeMap();

    config_map.put("service_port", ice_leaf_prefs.get("node_service_port", "2338"));
    config_map.put("tls_service_port", ice_leaf_prefs.get("node_tls_service_port", "2348"));

    config_map.put("db_path", ice_leaf_prefs.get("node_db_path", null));
    config_map.put("db_type", "rocksdb");
    config_map.put("tls_key_path", ice_leaf_prefs.get("node_tls_key_path", null));

    config_map.put("tx_index", ""+ice_leaf_prefs.getBoolean("node_tx_index", true));
    config_map.put("addr_index", ""+ice_leaf_prefs.getBoolean("node_addr_index", true));

    setMessageBox(config_map.toString());

    ConfigMem config = new ConfigMem(config_map);

    node = new SnowBlossomNode(config);

    setStatusBox("Node started");
    setMessageBox("");


  }

}
