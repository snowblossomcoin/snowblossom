package snowblossom.iceleaf.components;
import java.util.prefs.Preferences;
import javax.swing.JCheckBox;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

public class PersistentComponentCheckBox extends JCheckBox implements ChangeListener
{
  protected Preferences prefs;
  protected String pref_path;
  protected boolean value;

  public PersistentComponentCheckBox(Preferences iprefs, String label, String ipath, boolean default_value)
  {
    super(label, iprefs.getBoolean(ipath, default_value));

    this.prefs = iprefs;
    this.pref_path = ipath;

    this.value = prefs.getBoolean(pref_path, !default_value);

    this.addChangeListener(this);

    stateChanged(null);

  }

  public void stateChanged(ChangeEvent e)
  {
    if (isSelected() != value)
    {
      value = isSelected();
      prefs.putBoolean(pref_path, value);
    }
    
  }

}
