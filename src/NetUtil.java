package snowblossom;

import java.net.URL;
import java.util.Scanner;

public class NetUtil
{

  public static String getUrlLine(String url)
    throws java.io.IOException
  {
    URL u = new URL(url);
    Scanner scan =new Scanner(u.openStream());
    String line = scan.nextLine();
    scan.close();
    return line;
  }

}
