package snowblossom.iceleaf.components;
import java.util.prefs.Preferences;


public abstract class PersistentComponent
{
  protected Preferences prefs;
  protected String pref_path;

  public PersistentComponent(Preferences prefs, String path)
  {
    this.prefs = prefs;
    this.pref_path = path;

  }



}
