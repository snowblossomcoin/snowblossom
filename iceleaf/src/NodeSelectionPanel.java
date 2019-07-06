package snowblossom.iceleaf;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.util.prefs.Preferences;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;

import snowblossom.iceleaf.components.PersistentComponentTextArea;

public class NodeSelectionPanel
{
  private JPanel panel;
	protected Preferences ice_leaf_prefs;
  protected IceLeaf ice_leaf;

  protected JTextArea message_box;
  protected JTextArea status_box;
  protected PersistentComponentTextArea list_box;

  protected JRadioButton butt_local;
  protected JRadioButton butt_seed;
  protected JRadioButton butt_list;


  public NodeSelectionPanel(IceLeaf ice_leaf)
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

    panel.add(new JLabel("Select node to use"), c);

    ButtonGroup bg = new ButtonGroup();

    butt_local = new JRadioButton("local");
    butt_seed = new JRadioButton("seed");
    butt_list = new JRadioButton("list");

    bg.add(butt_local);
    bg.add(butt_seed);
    bg.add(butt_list);

    panel.add(butt_local, c);
    panel.add(butt_seed, c);
    c.gridwidth = 1;
    panel.add(butt_list, c);

    c.weightx=1.0;
    c.weighty=1.0;

    StringBuilder sb_list_default = new StringBuilder();
    for(String uri : ice_leaf.getParams().getSeedUris())
    {
      sb_list_default.append(uri);
      sb_list_default.append('\n');
    }

    list_box = new PersistentComponentTextArea(ice_leaf_prefs, "", "select_node_list_box",sb_list_default.toString());
    list_box.setRows(8);
    list_box.setColumns(120);


    c.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(list_box, c);

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

    setStatusBox("Startup"); 
    

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
