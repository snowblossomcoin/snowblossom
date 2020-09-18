package snowblossom.iceleaf;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Need an action listener to do a bunch of stuff, outside of the GUI thread
 * use this.  Just extend and implement threadActionPerformed.
 */
public abstract class ThreadActionListener implements ActionListener
{

  /**
   * This will be run in its own new thread on each action fired to this action listener
   */
  public abstract void threadActionPerformed(ActionEvent e);

  public void actionPerformed(ActionEvent e)
  {
    new ActionRunnerThread(e).start();
  }

  public class ActionRunnerThread extends Thread
  {
    private ActionEvent e;
    public ActionRunnerThread(ActionEvent e)
    {
      this.e = e;
    }
    
    public void run()
    {
      threadActionPerformed(e);
    }

  }

}
