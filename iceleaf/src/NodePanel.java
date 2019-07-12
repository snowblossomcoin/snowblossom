package snowblossom.iceleaf;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Dimension;
import java.util.prefs.Preferences;

import snowblossom.node.SnowBlossomNode;
import snowblossom.lib.Globals;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;

import duckutil.PeriodicThread;
import duckutil.ConfigMem;
import java.util.TreeMap;

public class NodePanel extends BasePanel
{
  protected SnowBlossomNode node;
  protected JProgressBar progress;
  protected boolean start_attempt;

  public NodePanel(IceLeaf ice_leaf)
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

    progress = new JProgressBar(0,0);
    panel.add(progress, c);

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
        int height = node.getBlockIngestor().getHeight();
        setProgressBar(height, net_height);

        sb.append("Height: " + height + " out of " + net_height +"\n");
        sb.append("Peers: " + node.getPeerage().getConnectedPeerCount() +"\n");
        sb.append("Node TLS key: " + AddressUtil.getAddressString(Globals.NODE_ADDRESS_STRING, node.getTlsAddress()) +"\n");

        setStatusBox(sb.toString().trim());

      }
      catch(Exception e)
      {
        String text = e.toString();
        setMessageBox(text);
       
      }

    }

  }

  public void setProgressBar(int curr, int net)
  {
    int enet = Math.max(net, curr);
    SwingUtilities.invokeLater(new Runnable() {
      public void run()
      {
        progress.setMaximum(enet);
        progress.setValue(curr);
      }
    });
  }




  private void startNode()
    throws Exception
  {
    start_attempt=true;
    TreeMap<String, String> config_map = new TreeMap();

    config_map.put("network", ice_leaf.getParams().getNetworkName());
    config_map.put("service_port", ice_leaf_prefs.get("node_service_port", null));
    config_map.put("tls_service_port", ice_leaf_prefs.get("node_tls_service_port", null));

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
