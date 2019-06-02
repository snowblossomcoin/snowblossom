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

      tab_pane.add("Node", new JPanel());
      tab_pane.add("Client", new JPanel());
      tab_pane.add("History", new JPanel());


    }
  }

}
