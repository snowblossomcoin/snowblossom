package snowblossom.iceleaf;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.JButton;
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
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import snowblossom.lib.NonsenseWordList;
import snowblossom.client.SeedWordList;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;


public class MakeWalletPanel
{
  private JPanel panel;
	protected Preferences ice_leaf_prefs;
  protected IceLeaf ice_leaf;

  protected JTextArea message_box;
  protected JTextField name_field;
  protected JTextField import_field;
  protected JButton random_name_button;
  protected JRadioButton seed_button = new JRadioButton("HD Seed - secp256k1");
  protected JRadioButton old_std_button = new JRadioButton("Old Standard - secp256k1");
  protected JRadioButton qhard_button = new JRadioButton("QHard - secp256k1 + rsa8192 + DSTU4145");
  protected JRadioButton import_seed_button = new JRadioButton("Import seed");
  protected JButton make_wallet_button;

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
      c.gridwidth = 1;
			c.anchor = GridBagConstraints.NORTHWEST;


    panel.add(new JLabel("Name for new wallet: "), c);
    name_field = new JTextField();
    name_field.setColumns(25);
    name_field.setText("meow");
    panel.add(name_field, c);

    c.gridwidth = GridBagConstraints.REMAINDER;
    random_name_button = new JButton("Random name");
    random_name_button.addActionListener( new RandomButtonAction());
    panel.add(random_name_button, c);

    c.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(new JLabel("Mode for new wallet: "), c);

    ButtonGroup main_radio_bg = new ButtonGroup();
    main_radio_bg.add(seed_button);
    main_radio_bg.add(old_std_button);
    main_radio_bg.add(qhard_button);
    main_radio_bg.add(import_seed_button);

    seed_button.setSelected(true);

    panel.add(seed_button, c);
    panel.add(old_std_button, c);
    panel.add(qhard_button, c);
    c.gridwidth=1;
    panel.add(import_seed_button, c);
    import_field = new JTextField();
    import_field.setColumns(20);
    c.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(import_field, c);

    make_wallet_button = new JButton("Make wallet");
    make_wallet_button.addActionListener( new RandomButtonAction());
    panel.add(make_wallet_button, c);

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

  public class RandomButtonAction implements ActionListener
  {
    public void actionPerformed(ActionEvent e)
    {
      StringBuilder sb = new StringBuilder();
      sb.append(NonsenseWordList.getNonsense(1));
      sb.append("-");
      sb.append(SeedWordList.getNonsense(1));
      name_field.setText(sb.toString());
      setMessageBox(sb.toString());

    }
  }
}
