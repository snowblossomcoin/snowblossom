package snowblossom.iceleaf;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class ErrorUtil
{
  public static String getThrowInfo(Throwable t)
  {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(bout);
    t.printStackTrace(out);
    out.close();

    return new String(bout.toByteArray());
  }

}
