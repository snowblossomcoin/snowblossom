package snowblossom.iceleaf;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.util.prefs.Preferences;

import snowblossom.iceleaf.components.PersistentComponentTextArea;
import snowblossom.iceleaf.components.PersistentComponentCheckBox;
import java.util.TreeSet;
import duckutil.PeriodicThread;
import java.util.Scanner;
import snowblossom.client.StubUtil;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import io.grpc.ManagedChannel;

import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import snowblossom.client.StubHolder;


public class NodeSelectionPanel extends BasePanel
{
  protected PersistentComponentTextArea list_box;

  private PersistentComponentCheckBox box_local;
  private PersistentComponentCheckBox box_seed;
  private PersistentComponentCheckBox box_list;

  protected volatile ManagedChannel channel;
  protected StubHolder stub_holder;

  public NodeSelectionPanel(IceLeaf ice_leaf)
  {
    super(ice_leaf);
    stub_holder = new StubHolder();
	}

  @Override
	public void setupPanel()
	{

			GridBagConstraints c = new GridBagConstraints();
			c.weightx = 0.0;
			c.weighty= 0.0;
			c.gridheight = 1;
			c.anchor = GridBagConstraints.WEST;

    c.gridwidth = GridBagConstraints.REMAINDER;

    panel.add(new JLabel("Select node sources to use.  The checked node sets will be considered.  The fastest will be used."), c);


    box_local = new PersistentComponentCheckBox(ice_leaf_prefs, "local", "node_selection_local", true);
    box_seed = new PersistentComponentCheckBox(ice_leaf_prefs, "seed", "node_selection_seed", true);
    box_list = new PersistentComponentCheckBox(ice_leaf_prefs, "list", "node_selection_list", false);


    panel.add(box_local, c);
    panel.add(box_seed, c);

    c.gridwidth = 1;
    panel.add(box_list, c);


    StringBuilder sb_list_default = new StringBuilder();
    for(String uri : ice_leaf.getParams().getSeedUris())
    {
      sb_list_default.append(uri);
      sb_list_default.append('\n');
    }

    list_box = new PersistentComponentTextArea(ice_leaf_prefs, "", "select_node_list_box",sb_list_default.toString());
    list_box.setRows(8);
    list_box.setColumns(40);


    c.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(list_box, c);

    setStatusBox("Startup"); 
    ChannelMaintThread cmt = new ChannelMaintThread();

    box_local.addChangeListener(cmt);
    box_seed.addChangeListener(cmt);
    box_list.addChangeListener(cmt);
    
    cmt.start();

  }

  public ManagedChannel getManagedChannel(){return channel;}
  public StubHolder getStubHolder(){return stub_holder;}

  public class ChannelMaintThread extends PeriodicThread implements ChangeListener
  {
    public ChannelMaintThread()
    {
      super(300000);
    }
    public void runPass() throws Exception
    {
      sleep(20);
      setStatusBox("Reconnecting");
      try
      {
        TreeSet<String> options = new TreeSet<>();
        if (ice_leaf_prefs.getBoolean("node_selection_local", true))
        {
          String uri = String.format("grpc://localhost:%s",ice_leaf_prefs.get("node_service_port", null));
          options.add(uri);
        }
        if (ice_leaf_prefs.getBoolean("node_selection_seed", true))
        {
          for(String uri : ice_leaf.getParams().getSeedUris())
          {
            options.add(uri);
          }
        }
        if (ice_leaf_prefs.getBoolean("node_selection_list", false))
        {
          Scanner scan = new Scanner(list_box.getText());
          while(scan.hasNext())
          {
            options.add(scan.next());
          }
        }
        StringBuilder msg = new StringBuilder();
        msg.append("Node option list:\n");
        for(String uri : options)
        {
          msg.append(uri);
          msg.append('\n');
        }
        setMessageBox(msg.toString().trim());

        long t1 = System.currentTimeMillis();

        StubUtil.ChannelMonitor mon = StubUtil.findFastestChannelMonitor(options, ice_leaf.getParams());
        long t2 = System.currentTimeMillis();
        if (mon != null)
        {
          channel = mon.getManagedChannel();
          stub_holder.update(channel);

          setStatusBox(String.format("Connected to %s and checked in %s ms",mon.getUri(), t2-t1));
        }

      }
      catch(Throwable t)
      {
        setMessageBox(t.toString());
      }

    }

    private volatile String last_state="";
    public void stateChanged(ChangeEvent e)
    {
      String state = "" + box_local.isSelected() + box_seed.isSelected() + box_list.isSelected();

      if (!state.equals(last_state))
      {
        last_state = state;
        this.wake();
      }
    }
     

  }
}
