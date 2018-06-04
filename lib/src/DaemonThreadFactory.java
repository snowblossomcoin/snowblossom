package snowblossom.lib;

import java.util.concurrent.ThreadFactory;

public class DaemonThreadFactory implements ThreadFactory {

  long count;
  String name;

  public DaemonThreadFactory(String name)
  {
    this.name = name;
    this.count = 0L;
  }

  @Override
  public synchronized Thread newThread(Runnable r)
  {
	  Thread t = new Thread(r);
		t.setDaemon(true);
    t.setName(name +"{" + count + "}");
    count++;
		return t;
  }
}
