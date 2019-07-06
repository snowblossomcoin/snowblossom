package snowblossom.iceleaf.components;
import java.util.prefs.Preferences;
import javax.swing.JTextField;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class PersistentComponentTextField extends JTextField implements ActionListener, DocumentListener
{
  protected Preferences prefs;
  protected String pref_path;
  protected String value;

  public PersistentComponentTextField(Preferences iprefs, String label, String ipath, String default_value, int cols)
  {
    super(iprefs.get(ipath, default_value));

    this.prefs = iprefs;
    this.pref_path = ipath;

    this.value = prefs.get(pref_path, default_value);

    this.addActionListener(this);
    this.getDocument().addDocumentListener(this);
    this.setColumns(cols);
  }

  private void updateInternal()
  {
    if (!getText().equals(value))
    {
      value = getText();
      prefs.put(pref_path, value);
    }
 
  }

  public void actionPerformed(ActionEvent e)
  {
    updateInternal();
    
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





}
