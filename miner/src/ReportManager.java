package snowblossom.miner;

import java.util.TreeMap;
import duckutil.RateReporter;
import java.text.DecimalFormat;
import duckutil.AtomicFileOutputStream;
import java.io.PrintStream;
import java.io.File;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Map;
import java.util.TreeSet;

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

      PrintStream out = new PrintStream(new AtomicFileOutputStream( path ));

      DecimalFormat df = new DecimalFormat("0.0");
      out.println("Total: " + total.getReportLong(df));

      TreeSet<String> to_remove = new TreeSet<>();
      for(Map.Entry<String, RateReporter> me : rate_map.entrySet())
      {
        if (me.getValue().isZero())
        {
          to_remove.add(me.getKey());
        }
        else
        {
          out.println(me.getKey() + " " + me.getValue().getReportLong(df));
        }
      }

      for(String k : to_remove)
      {
        rate_map.remove(k);
      }

      out.flush();
      out.close();
    }
    catch(Exception e)
    {
      logger.log(Level.WARNING, "Error writing report: " + e.toString());
    }

  }

}
