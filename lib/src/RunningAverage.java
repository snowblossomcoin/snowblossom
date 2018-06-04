package snowblossom.lib;

import java.math.BigInteger;
import java.util.LinkedList;


public class RunningAverage
{
  private LinkedList<Long> values;
  private int keep;
  private BigInteger sum;

  public RunningAverage(int keep)
  {
    this.keep = keep;
    sum = BigInteger.ZERO;
    values = new LinkedList<Long>();
  }

  public synchronized void add(Long l)
  {
    values.add(l);
    while(values.size() > keep)
    {
      long f = values.poll();
      sum = sum.subtract(BigInteger.valueOf(f));
    }
    sum = sum.add(BigInteger.valueOf(l));
  }

  public synchronized long get()
  {
    return sum.divide(BigInteger.valueOf(keep)).longValue();
  }


}
