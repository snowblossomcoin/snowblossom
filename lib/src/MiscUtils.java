package snowblossom.lib;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;

public class MiscUtils
{
  public static String printStackTrace(Throwable t)
  {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PrintStream pout = new PrintStream(bout);

    t.printStackTrace(pout);

    return new String(bout.toByteArray());
  }


  public static String getAgeSummary(double ms)
  {
    double t = ms / 1000.0;
    String unit="seconds";

    if (t > 100.0 )
    {
      t /= 60.0;
      unit ="minutes";
    }
    if (t > 100.0 )
    {
      t /= 60.0;
      unit ="hours";
    }
    if (t > 48.0)
    {
      t /= 24.0;
      unit = "days";
    }

    DecimalFormat df = new DecimalFormat("0.0");

    return String.format("%s %s", df.format(t), unit);

  }
}
