package snowblossom.iceleaf;

import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

import snowblossom.lib.Globals;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.NetworkParamsProd;
import java.util.prefs.Preferences;
import snowblossom.iceleaf.components.*;
import java.io.File;
import java.util.LinkedList;


import snowblossom.client.StubHolder;
import java.awt.Font;
import javax.swing.plaf.FontUIResource;
import javax.imageio.ImageIO;
import java.io.InputStream;
import java.awt.Color;


public class IceLeaf
{
  public static void main(String args[]) throws Exception
  {
    Globals.addCryptoProvider();

    new IceLeaf(new NetworkParamsProd(), null);

  }

  protected Preferences ice_leaf_prefs;
  protected NodePanel node_panel;
  protected NodeSelectionPanel node_select_panel;
  protected WalletPanel wallet_panel;
  protected MakeWalletPanel make_wallet_panel;
  protected SendPanel send_panel;
  protected AddressPanel address_panel;
  protected NetworkParams params;
  protected SettingsPanel settings_panel;
  protected IceLeaf ice_leaf;

  public Preferences getPrefs() { return ice_leaf_prefs;}
  public NetworkParams getParams() { return params; }
  public StubHolder getStubHolder(){return node_select_panel.getStubHolder();}
  public WalletPanel getWalletPanel(){return wallet_panel;}
  public SendPanel getSendPanel(){return send_panel;}

  public Color getBGColor(){return new Color(204,204,255);}
  public Color getTextAreaBGColor(){return new Color(220,220,220);}

  private Font fixed_font;
  private Font var_font;

  public Font getFixedFont(){return fixed_font;}
  public Font getVariableFont(){return var_font;}
  

  public IceLeaf(NetworkParams params, Preferences prefs)
    throws Exception
  {
    this.params = params;
    this.ice_leaf_prefs = prefs;
    this.ice_leaf = this;
    if (ice_leaf_prefs == null)
    {
      ice_leaf_prefs = Preferences.userNodeForPackage(this.getClass());
    }
    
    SwingUtilities.invokeAndWait(new EnvSetup());

    node_panel = new NodePanel(this);
    node_select_panel = new NodeSelectionPanel(this);
    wallet_panel = new WalletPanel(this);
    make_wallet_panel = new MakeWalletPanel(this);
    send_panel = new SendPanel(this);
    address_panel = new AddressPanel(this);
    settings_panel = new SettingsPanel(this);

    SwingUtilities.invokeLater(new WindowSetup());

  }


  public class EnvSetup implements Runnable
  {
    public void run()
    {
      try
      {
        fixed_font = Font.createFont(Font.TRUETYPE_FONT, 
          IceLeaf.class.getResourceAsStream("/iceleaf/resources/font/Hack-Regular.ttf"));
        var_font = new Font("Verdana", 0, 12);

        //IceLeaf.setUIFont(new Font("Verdana", 0, 12));
        //UIManager.setLookAndFeel(new javax.swing.plaf.nimbus.NimbusLookAndFeel());
      }
      catch(Exception e)
      {
        e.printStackTrace();
      }
    }
  }




  public class WindowSetup implements Runnable
  {
    public void run()
    {

      JFrame f=new JFrame();
      f.setVisible(true);
      f.setDefaultCloseOperation( f.EXIT_ON_CLOSE);
      f.setBackground(getBGColor());

      try
      {
        InputStream is = IceLeaf.class.getResourceAsStream("/iceleaf/resources/flower-with-ink-256.png");
        f.setIconImage(ImageIO.read(is));

      }
      catch(Exception e)
      {
        e.printStackTrace();
      }


      String title = "SnowBlossom - IceLeaf " + Globals.VERSION;
      if (!params.getNetworkName().equals("snowblossom"))
      {
        title = title + " - " + params.getNetworkName();
      }
      f.setTitle(title);
      f.setSize(950, 600);

      JTabbedPane tab_pane = new JTabbedPane();

      f.setContentPane(tab_pane);

      // should be first to initialize settings to default
      settings_panel.setup();

      node_panel.setup();
      node_select_panel.setup();
      wallet_panel.setup();
      make_wallet_panel.setup();
      send_panel.setup();
      address_panel.setup();



      tab_pane.add("Wallets", wallet_panel.getPanel());
      tab_pane.add("Send", send_panel.getPanel());
      tab_pane.add("Addresses", address_panel.getPanel());
      tab_pane.add("Make Wallet", make_wallet_panel.getPanel());
      tab_pane.add("Node Selection", node_select_panel.getPanel());
      tab_pane.add("Node", node_panel.getPanel());
      tab_pane.add("Settings", settings_panel.getPanel());

      UIUtil.applyLook(f, ice_leaf);
      
    }

  }
	public static void setUIFont(Font f) 
  {
    for(Object key : UIManager.getDefaults().keySet())
		{
      Font orig = UIManager.getFont(key);
      if (orig != null)
      {
        Font font = new Font(f.getFontName(), orig.getStyle(), f.getSize());
        UIManager.put(key, new FontUIResource(font));
			}
		}
	}

}
