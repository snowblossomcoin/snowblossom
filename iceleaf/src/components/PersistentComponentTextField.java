package snowblossom.iceleaf.components;
import java.util.prefs.Preferences;
import javax.swing.JTextField;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class PersistentComponentTextField extends JTextField implements ActionListener
{
  protected Preferences prefs;
  protected String pref_path;
  protected String value;

  public PersistentComponentTextField(Preferences iprefs, String label, String ipath, String default_value)
  {
    super(iprefs.get(ipath, default_value));

    this.prefs = iprefs;
    this.pref_path = ipath;

    this.value = prefs.get(pref_path, default_value);

    this.addActionListener(this);


  }

  public void actionPerformed(ActionEvent e)
  {
    if (!getText().equals(value))
    {
      value = getText();
      prefs.put(pref_path, value);
    }
    
  }





}
