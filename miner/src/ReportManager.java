package snowblossom.miner;

import java.util.TreeMap;
import duckutil.RateReporter;

public class ReportManager
{
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

  }

}
