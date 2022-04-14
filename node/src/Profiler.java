package snowblossom.node;

import duckutil.PeriodicThread;
import duckutil.TimeRecord;
import java.io.PrintStream;

public class Profiler extends PeriodicThread
{
  private final PrintStream pout;
  private final TimeRecord time_record;

  public Profiler(long period, PrintStream pout)
  {
    super(period);
    setName("profiler");
    setDaemon(true);

    this.pout = pout;

    time_record = new TimeRecord();
    TimeRecord.setSharedRecord(time_record);
  }

  @Override
  public void runPass()
  {
    pout.println("-----------------------------------------------");
    time_record.printReport(pout);
    time_record.reset();
  }

}
