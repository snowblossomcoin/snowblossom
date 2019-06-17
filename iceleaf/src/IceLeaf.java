package snowblossom.iceleaf;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import snowblossom.lib.Globals;

public class IceLeaf
{
  public static void main(String args[]) throws Exception
  {
    System.out.println(System.getProperty("user.home"));
    new IceLeaf();

  }


  public IceLeaf()
  {
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
      tab_pane.add("Node", new JPanel());

      // Which node to use for client data
      tab_pane.add("NodeClient", new JPanel());


      // Wallets to load
      tab_pane.add("Wallets", new JPanel());


    }
  }

}
