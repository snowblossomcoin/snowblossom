package snowblossom.gclient;

import duckutil.ConfigFile;
import snowblossom.gclient.views.AddressListView;
import snowblossom.lib.Globals;

import javax.swing.*;
import java.io.File;

public class Client
{


  private AddressListView addressList;

  public static void main(String[] args) throws Exception
  {
    Globals.addCryptoProvider();
    ConfigFile config = new ConfigFile(args[0]);
    Client c = new Client();
    c.init(config);
  }

  public void init(ConfigFile config) throws Exception
  {
    File walletFile = new File(config.get("wallet_path") + File.separator + "wallet.db");

    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    JFrame frame = new JFrame("Client");
    frame.setSize(500, 500);

    addressList = new AddressListView(walletFile, config);

    frame.setContentPane(addressList);

    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.pack();
    frame.setVisible(true);

    System.out.println("refreshing");
    addressList.refreshAddresses();
  }

}
