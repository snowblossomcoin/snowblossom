package snowblossom.iceleaf.components;

import javax.swing.JLabel;
import java.awt.Color;
import java.awt.event.MouseListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.awt.Desktop;
import java.awt.Cursor;

public class LinkLabel extends JLabel
{
  private URI location;

  public LinkLabel(String url, String text)
  {
    try
    {
    setForeground(Color.BLUE.darker());
    setText(text);

    this.location = new URI(url);

    addMouseListener(new Clicker());
    setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }

  }

  public class Clicker extends MouseAdapter
  {
    @Override
    public void mouseClicked(MouseEvent e)
    {
      try
      {
        Desktop.getDesktop().browse(location);
      }
      catch(Exception e2)
      {
        e2.printStackTrace();
      }
    }
  }

}
