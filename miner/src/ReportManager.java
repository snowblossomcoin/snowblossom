package snowblossom.miner;

import java.util.TreeMap;
import duckutil.RateReporter;
import java.text.DecimalFormat;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.File;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Map;

public class ReportManager
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  TreeMap<String, RateReporter> rate_map;
  RateReporter total;
  
  
  public ReportManager()
  {
    total = new RateReporter();
    rate_map = new TreeMap<>();

  }

  public synchronized void record(String address, long count)
  {
    total.record(count);
    if (!rate_map.containsKey(address))
    {
      rate_map.put(address, new  RateReporter());
    }
    rate_map.get(address).record(count);
  }

  public RateReporter getTotalRate()
  {
    return total;
  }

  public synchronized void writeReport(String path)
  {
    try
    {

    String tmppath = path + ".tmp";
    PrintStream out = new PrintStream(new FileOutputStream( tmppath ));

    DecimalFormat df = new DecimalFormat("0.0");
    out.println("Total: " + total.getReport(df));

    for(Map.Entry<String, RateReporter> me : rate_map.entrySet())
    {
      out.println(me.getKey() + " " + me.getValue().getReport(df));
    }


    out.flush();
    out.close();

    new File(tmppath).renameTo(new File(path));
    }
    catch(Exception e)
    {
      logger.log(Level.WARNING, "Error writing report: " + e.toString());
    }

  }

}
