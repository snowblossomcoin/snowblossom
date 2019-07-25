package snowblossom.iceleaf;

import duckutil.PeriodicThread;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import javax.swing.JLabel;
import snowblossom.client.SnowBlossomClient;
import snowblossom.client.WalletUtil;
import snowblossom.lib.AddressSpecHash;
import snowblossom.proto.BalanceInfo;
import snowblossom.proto.WalletDatabase;
import snowblossom.util.proto.*;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.awt.Image;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

public class ReceivePanel extends BasePanel
{

  public static final int qr_size=300;

  protected WalletComboBox wallet_select_box;
  protected UpdateThread update_thread;
  protected JLabel address_qr_label;

  public ReceivePanel(IceLeaf ice_leaf)
  {
    super(ice_leaf);
	}

	@Override
  public void setupPanel()
	{
    
    GridBagConstraints c = new GridBagConstraints();
    c.weightx = 0.0;
    c.weighty= 0.0;
    c.gridheight = 1;
    c.anchor = GridBagConstraints.WEST;

    c.gridwidth = 1;
    panel.add(new JLabel("Wallet to view addresses of:"), c);
    c.gridwidth = GridBagConstraints.REMAINDER;

    wallet_select_box = new WalletComboBox(ice_leaf);
    panel.add(wallet_select_box, c);

    address_qr_label = new JLabel();

    panel.add(address_qr_label, c);

 
    update_thread = new UpdateThread();
    update_thread.start();
    
    wallet_select_box.addActionListener(new ActionListener(){
      public void actionPerformed(ActionEvent e)
      {
        update_thread.wake();
      }
    });

  }

  public class UpdateThread extends PeriodicThread
  {
    public UpdateThread()
    {
      super(45000);

    }
    public void runPass()
    {
			try
			{
        String wallet_name = (String)wallet_select_box.getSelectedItem();
        if (wallet_name == null)
        {
          setMessageBox("no wallet selected");
          setStatusBox("");
          return;
        }
        
        SnowBlossomClient client = ice_leaf.getWalletPanel().getWallet( wallet_name );
        if (client == null)
        {
          setMessageBox("no wallet selected");
          setStatusBox("");
          return;
        }
        
        AddressSpecHash spec = client.getPurse().getUnusedAddress(false,false);
        String address_str = spec.toAddressString(ice_leaf.getParams());

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bit_matrix = qrCodeWriter.encode(address_str, BarcodeFormat.QR_CODE, qr_size, qr_size);

        BufferedImage bi = MatrixToImageWriter.toBufferedImage(bit_matrix);

        setQrImage(bi);

        setStatusBox(address_str);
        setMessageBox("");

			}
			catch(Throwable t)
			{
				setMessageBox(ErrorUtil.getThrowInfo(t));
        setStatusBox("");
			}
    }

  }

  private void setQrImage(Image img)
  {
    ImageIcon ii = new ImageIcon(img);
    SwingUtilities.invokeLater(new Runnable() {
      public void run()
      {
				address_qr_label.setIcon(ii);
      }
    });


  }


}
