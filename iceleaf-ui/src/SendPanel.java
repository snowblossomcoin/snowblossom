package snowblossom.iceleaf;

import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import snowblossom.client.SnowBlossomClient;
import snowblossom.client.TransactionFactory;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.ChainHash;
import snowblossom.lib.Globals;
import snowblossom.lib.TransactionUtil;
import snowblossom.proto.SubmitReply;
import snowblossom.proto.TransactionOutput;
import snowblossom.util.proto.*;

public class SendPanel extends BasePanel
{
  protected WalletComboBox wallet_select_box;

  protected JTextField dest_field;
  protected JTextField send_amount_field;
  protected JProgressBar send_bar;
  protected JButton send_button;

  // state = 0 - init
  // state = 1 - clock running
  // state = 2 - ready
  private int send_state=0;
  private String saved_dest;
  private String saved_amount;
  private String saved_wallet;
  private Object state_obj = new Object();
  private TransactionFactoryResult tx_result;

  public static final int SEND_DELAY=10000;
  public static final int SEND_DELAY_STEP=50;

  public SendPanel(IceLeaf ice_leaf)
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
    panel.add(new JLabel("Wallet to send from:"), c);
    c.gridwidth = GridBagConstraints.REMAINDER;

    wallet_select_box = new WalletComboBox(ice_leaf);
    panel.add(wallet_select_box, c);

    c.gridwidth = 1;
    panel.add(new JLabel("Destination address:"), c);
    c.gridwidth = GridBagConstraints.REMAINDER;

    dest_field = new JTextField();
    dest_field.setColumns(65);
    panel.add(dest_field, c);


    c.gridwidth = 1;
    panel.add(new JLabel("Send amount (or 'all'):"), c);
    c.gridwidth = GridBagConstraints.REMAINDER;

    send_amount_field = new JTextField();
    send_amount_field.setColumns(15);
    panel.add(send_amount_field, c);

    send_bar = new JProgressBar(0, SEND_DELAY);
    panel.add(send_bar, c);

    send_button = new JButton("Send");
    panel.add(send_button, c);

    send_button.addActionListener(new SendButtonListner());


  }

  public class SendButtonListner implements ActionListener
  {
    public void actionPerformed(ActionEvent e)
    {
      new SendButtonThread().start();

    }
  }

  public class SendButtonThread extends Thread
  {
    public void run()
    {

			try
			{
				synchronized(state_obj)
				{
					if (send_state == 1) return;
					if (send_state == 2)
					{
            if (!saved_dest.equals(dest_field.getText()))
            {
              throw new Exception("Parameters changed before second send press");
            }
            if (!saved_amount.equals(send_amount_field.getText()))
            {
              throw new Exception("Parameters changed before second send press");
            }
            if (!saved_wallet.equals(wallet_select_box.getSelectedItem()))
            {
              throw new Exception("Parameters changed before second send press");
            }
            SubmitReply reply = ice_leaf.getStubHolder().getBlockingStub().submitTransaction(tx_result.getTx());
            ChainHash tx_hash = new ChainHash(tx_result.getTx().getTxHash());
            setMessageBox(String.format("%s\n%s", tx_hash.toString(), reply.toString()));
            setStatusBox("");

						send_state = 0;
						setProgressBar(0, SEND_DELAY);
            setStatusBox("");

						return;
					}
					if (send_state == 0)
					{
						saved_dest = dest_field.getText();
						saved_amount = send_amount_field.getText();
            saved_wallet = (String)wallet_select_box.getSelectedItem();
						send_state = 1;
					}
				}

        setupTx();


        setStatusBox("Time delay to review");
				for(int i=0; i<SEND_DELAY; i+=SEND_DELAY_STEP)
				{
					setProgressBar(i, SEND_DELAY);
					sleep(SEND_DELAY_STEP);
				}
        setStatusBox("Ready to broadcast");
				setProgressBar(SEND_DELAY, SEND_DELAY);
				synchronized(state_obj)
				{
					send_state=2;
				}
			}
			catch(Throwable t)
			{
        setStatusBox("Error");
				setMessageBox(ErrorUtil.getThrowInfo(t));
				
				synchronized(state_obj)
				{
					send_state=0;
				}
			}
    }

  }

  private void setupTx() throws Exception
  {
    setStatusBox("Creating transaction");
    setMessageBox("");
    TransactionFactoryConfig.Builder config = TransactionFactoryConfig.newBuilder();
    config.setSign(true);
    config.setChangeFreshAddress(true);
    config.setInputConfirmedThenPending(true);
    config.setFeeUseEstimate(true);
    
    AddressSpecHash dest_addr = new AddressSpecHash(saved_dest.trim(), ice_leaf.getParams());

    long output_val = 0;
    if (saved_amount.toLowerCase().equals("all"))
    {
      config.setSendAll(true);
    }
    else
    {
      output_val = (long) Double.parseDouble(saved_amount) * Globals.SNOW_VALUE;
    }

    config.addOutputs( TransactionOutput.newBuilder().setValue(output_val).setRecipientSpecHash(dest_addr.getBytes()).build() );

    SnowBlossomClient client = ice_leaf.getWalletPanel().getWallet( saved_wallet);
    if (client == null)
    {
      throw new Exception("Must specify source wallet");
    }

    tx_result = TransactionFactory.createTransaction(config.build(), client.getPurse().getDB(), client);

    setMessageBox(String.format("Press Send again to when progress bar is full to send:\n%s",
      TransactionUtil.prettyDisplayTx(tx_result.getTx(), ice_leaf.getParams())));
    
  }

  public void setProgressBar(int curr, int net)
		throws Exception
  {
    int enet = Math.max(net, curr);
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run()
      {
        send_bar.setMaximum(enet);
        send_bar.setValue(curr);
      }
    });
  }

}
