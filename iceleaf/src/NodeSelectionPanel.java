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

public class NodeSelectionPanel
{
  private JPanel panel;
	protected Preferences ice_leaf_prefs;

  protected JTextArea message_box;
  protected JTextArea status_box;


  public NodeSelectionPanel(IceLeaf ice_leaf)
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
