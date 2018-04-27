package snowblossom;

import java.util.TreeMap;
import java.util.Map;
import java.text.DecimalFormat;
import java.io.PrintStream;

public class TimeRecord
{
  private TreeMap<String, Long> times=new TreeMap<String, Long>();
  private TreeMap<String, Long> counts=new TreeMap<String, Long>();

  public synchronized void addTime(long tm, String name)
  {
    addTime(tm, name, 1L);
  }
  public synchronized void addTime(long tm, String name, long count)
  {
    {
      Long prev = times.get(name);
      long p = 0;
      if (prev != null) p = prev;

      times.put(name, p + tm);
    }
    {
      Long prev = counts.get(name);
      long p = 0;
      if (prev != null) p = prev;

      counts.put(name, p + count);


    }

  }

  public synchronized void printReport(PrintStream out)
  {
    DecimalFormat df = new DecimalFormat("0.000");

    for(Map.Entry<String, Long> me : times.entrySet())
    {
      String name = me.getKey();
      long nanosec = me.getValue();
      double seconds = nanosec / 1e9;
      out.println("  " + name + " - " + df.format(seconds) + " seconds " + counts.get(name) + " calls");
    }

  }

  public synchronized void reset()
  {
    times.clear();
    counts.clear();
  }

  private static TimeRecord shared;
  public static void setSharedRecord(TimeRecord s)
  {
    shared = s;
  }

  public static void record(long start, String name)
  {
    record(start, name, 1L);
  }
  public static void record(long start, String name, long count)
  {
    if (shared != null)
    {
      shared.addTime(System.nanoTime() - start, name, count);
    }

  }


}

