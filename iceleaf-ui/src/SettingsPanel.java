package snowblossom.iceleaf;

import java.awt.GridBagConstraints;
import java.io.File;
import javax.swing.JLabel;
import snowblossom.iceleaf.components.*;
import snowblossom.lib.NetworkParams;

public class SettingsPanel extends BasePanel
{
	protected NetworkParams params;

  public SettingsPanel(IceLeaf ice_leaf)
  {
    super(ice_leaf);
		this.params = ice_leaf.getParams();
  }

  @Override
  public void setupPanel()
  {
			GridBagConstraints c = new GridBagConstraints();
			c.weightx = 0.0;
			c.weighty= 0.0;
			c.gridheight = 1;
			c.anchor = GridBagConstraints.WEST;

			c.gridwidth = 1;
			panel.add(new JLabel("Wallet Directory"), c);
			c.gridwidth = GridBagConstraints.REMAINDER;
			File default_wallet_path = new File(SystemUtil.getImportantDataDirectory(params), "wallets");
			panel.add(new PersistentComponentTextField(ice_leaf_prefs, "", "wallet_path", default_wallet_path.toString(),70),c);

			c.gridwidth = GridBagConstraints.REMAINDER;
			panel.add(new PersistentComponentCheckBox(ice_leaf_prefs, "Run local node", "node_run_local", false), c);

			c.gridwidth = 1;
			panel.add(new JLabel("Service Port"), c);
			c.gridwidth = GridBagConstraints.REMAINDER;
			panel.add(new PersistentComponentTextField(ice_leaf_prefs, "", "node_service_port", ""+params.getDefaultPort(),8),c);

			c.gridwidth = 1;
			panel.add(new JLabel("TLS Service Port"), c);
			c.gridwidth = GridBagConstraints.REMAINDER;
			panel.add(new PersistentComponentTextField(ice_leaf_prefs, "", "node_tls_service_port", ""+params.getDefaultTlsPort(),8),c);


			c.gridwidth = 1;
			panel.add(new JLabel("Node DB Directory"), c);
			c.gridwidth = GridBagConstraints.REMAINDER;
			File default_node_db_path = new File(SystemUtil.getNodeDataDirectory(params), "node_db");
			panel.add(new PersistentComponentTextField(ice_leaf_prefs, "", "node_db_path", default_node_db_path.toString(),70),c);

			c.gridwidth = 1;
			panel.add(new JLabel("Node TLS Key Directory"), c);
			c.gridwidth = GridBagConstraints.REMAINDER;
			File default_node_tls_key_path = new File(SystemUtil.getNodeDataDirectory(params), "node_tls_key");
			panel.add(new PersistentComponentTextField(ice_leaf_prefs, "", "node_tls_key_path", default_node_tls_key_path.toString(),70),c);

			c.gridwidth = GridBagConstraints.REMAINDER;
			panel.add(new PersistentComponentCheckBox(ice_leaf_prefs, "Node transaction index", "node_tx_index", true), c);
			panel.add(new PersistentComponentCheckBox(ice_leaf_prefs, "Node address index", "node_addr_index", true), c);

      panel.add(new JLabel("NOTE: almost all changes here will require a restart to take effect.\n"),c);

  }


}
