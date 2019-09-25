package snowblossom.iceleaf;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.prefs.Preferences;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import snowblossom.util.proto.*;

public abstract class BasePanel
{
  protected JScrollPane outer_panel;
  protected JPanel panel;
	protected Preferences ice_leaf_prefs;
  protected IceLeaf ice_leaf;

  protected JTextArea status_box;
  protected JTextArea message_box;


  public BasePanel(IceLeaf ice_leaf)
  {
    this.ice_leaf = ice_leaf;
		ice_leaf_prefs = ice_leaf.getPrefs();
		GridBagLayout grid_bag = new GridBagLayout();
		panel = new JPanel(grid_bag);
    outer_panel = new JScrollPane(panel);
    
    panel.setBackground(ice_leaf.getBGColor());
		status_box = new JTextArea();
		message_box = new JTextArea();

    /*if (ice_leaf.getFixedFont() != null)
    {
      status_box.setFont(ice_leaf.getFixedFont().deriveFont(0,12));
      message_box.setFont(ice_leaf.getFixedFont().deriveFont(0,12));
    }*/

	}

	public abstract void setupPanel();

  public void setup()
  {
  	setupPanel(); 

    GridBagConstraints c = new GridBagConstraints();
    c.gridheight = 1;
		c.gridwidth = GridBagConstraints.REMAINDER;
    c.anchor = GridBagConstraints.WEST;

    c.weightx=0.0;
    c.weighty=0.0;

	  panel.add(new JLabel("Status:"),c);	

    status_box.setEditable(false);
    panel.add(status_box,c);
	  
    panel.add(new JLabel("Message:"),c);	

    message_box.setEditable(false);
    panel.add(message_box,c);
 
    c.anchor = GridBagConstraints.SOUTHEAST;
    c.weightx=1.0;
    c.weighty=1.0;
    panel.add(new JLabel("Snowblossom"),c);	
  }

  public JComponent getPanel(){return outer_panel;}

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
