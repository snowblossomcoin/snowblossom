package snowblossom.lib;

public class UpClock
{
  private static long last_time = 0L;

  public synchronized static long time()
  {
    long tm = Math.max(System.currentTimeMillis(), last_time+1);

    last_time = tm;

    return tm;
  }
}

