package snowblossom.iceleaf;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

import snowblossom.lib.Globals;
import java.util.prefs.Preferences;
import snowblossom.iceleaf.components.*;

public class IceLeaf
{
  public static void main(String args[]) throws Exception
  {
    System.out.println(System.getProperty("user.home"));
    new IceLeaf();

  }

  protected Preferences ice_leaf_prefs;


  public IceLeaf()
  {

    ice_leaf_prefs = Preferences.userNodeForPackage(this.getClass());

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

      // Run a node?
      tab_pane.add("Node", assembleNodePanel());

      // Which node to use for client data
      tab_pane.add("NodeClient", new JPanel());

      // Wallets to load
      tab_pane.add("Wallets", new JPanel());

    }

    public JPanel assembleNodePanel()
    {
      GridBagLayout grid_bag = new GridBagLayout();
      JPanel panel = new JPanel(grid_bag);

      {
        GridBagConstraints c = new GridBagConstraints();
        c.weightx = 1.0;
        c.weighty= 1.0;
        c.gridheight = 1;
        c.anchor = GridBagConstraints.  NORTHWEST;


        c.gridwidth = GridBagConstraints.REMAINDER;

        panel.add(new PersistentComponentCheckBox(ice_leaf_prefs, "Run local node", "node_run_local", false), c);
        
        c.gridwidth = 1;
        panel.add(new JLabel("Service Port"), c);
        c.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(new PersistentComponentTextField(ice_leaf_prefs, "", "node_service_port", "2880"),c);


      }
     


      return panel;


    }
  }

}
