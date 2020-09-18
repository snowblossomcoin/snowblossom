package snowblossom.lib;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class MiscUtils
{
  public static String printStackTrace(Throwable t)
  {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PrintStream pout = new PrintStream(bout);

    t.printStackTrace(pout);

    return new String(bout.toByteArray());
  }

}
