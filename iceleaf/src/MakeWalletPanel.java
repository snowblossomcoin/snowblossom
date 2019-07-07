package snowblossom.iceleaf;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
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

public class MakeWalletPanel
{
  private JPanel panel;
	protected Preferences ice_leaf_prefs;
  protected IceLeaf ice_leaf;

  protected JTextArea message_box;
  protected JTextField name_field;


  public MakeWalletPanel(IceLeaf ice_leaf)
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
    panel.add(new JLabel("Name for new wallet: "), c);
    c.gridwidth = GridBagConstraints.REMAINDER;
    name_field = new JTextField();
    panel.add(name_field, c);

    c.gridwidth = GridBagConstraints.REMAINDER;
    c.weightx=1.0;
    c.weighty=1.0;
 
    message_box = new JTextArea();
    message_box.setEditable(false);
    panel.add(message_box,c);

  }

  public JPanel getPanel()
  {
		return panel;
  }

  public class WalletUpdateThread extends PeriodicThread
  {
    public WalletUpdateThread()
    {
      super(5000);
    }

    public void runPass() throws Exception
    {
      /*try
      {

      }
      catch(Exception e)
      {
        String text = e.toString();
        setMessageBox(text);
       
      }*/

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
