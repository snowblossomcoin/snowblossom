package snowblossom.iceleaf;

import com.google.common.collect.ImmutableList;
import duckutil.ConfigMem;
import duckutil.PeriodicThread;
import java.awt.GridBagConstraints;
import java.util.TreeMap;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.Globals;
import snowblossom.lib.MiscUtils;
import snowblossom.lib.SystemUtil;
import snowblossom.node.SnowBlossomNode;
import snowblossom.node.StatusInterface;
import snowblossom.node.StatusLogger;

public class NodePanel extends BasePanel implements StatusInterface
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
        if (!SystemUtil.isJvm64Bit())
        {
          setStatusBox("Node requires a 64-bit JVM");
          setMessageBox("Snowblososm node uses rocksdb, which requires a 64-bit JVM to run.\n"
           + "See https://wiki.snowblossom.org/index.php/Download to download 64-bit JVM");
          return;
        }
        if (!start_attempt)
        {
          startNode();
        }

        if (node == null) return;

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
        String text = MiscUtils.printStackTrace(e);
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
    //There are too many side effects to try this more than once
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

    node = new SnowBlossomNode(config, ImmutableList.of(new StatusLogger(), this));

    setStatusBox("Node started");
    setMessageBox("");
  }

  public void setStatus(String msg)
  {
    this.setStatusBox(msg);
  }


}
