package snowblossom.iceleaf.components;
import java.util.prefs.Preferences;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Dimension;

public class PersistentComponentTextArea extends JTextArea implements DocumentListener
{
  protected Preferences prefs;
  protected String pref_path;
  protected String value;

  public PersistentComponentTextArea(Preferences iprefs, String label, String ipath, String default_value)
  {
    super(iprefs.get(ipath, default_value));

    this.prefs = iprefs;
    this.pref_path = ipath;

    this.value = prefs.get(pref_path, null);

    this.getDocument().addDocumentListener(this);

    updateInternal();
  }

  private void updateInternal()
  {
    if (!getText().equals(value))
    {
      value = getText();
      prefs.put(pref_path, value);
    }
 
  }

  public void  changedUpdate(DocumentEvent e)
  {
    updateInternal();
  }
  public void insertUpdate(DocumentEvent e)
  {
    updateInternal();
  }
  public void removeUpdate(DocumentEvent e)
  {
    updateInternal();
  }


  @Override
  public Dimension getMinimumSize()
  {
    return new Dimension( 
      Math.max(250, (int)Math.ceil(super.getMinimumSize().getWidth())),
      Math.max(100, (int)Math.ceil(super.getMinimumSize().getHeight())));
  }


}
