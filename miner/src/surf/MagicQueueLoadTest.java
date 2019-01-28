package snowblossom.miner.surf;

import java.util.Random;
import java.nio.ByteBuffer;
import duckutil.FusionInitiator;
import java.util.concurrent.Semaphore;
import java.text.DecimalFormat;

public class MagicQueueLoadTest
{
  public static void main(String args[]) throws Exception
  {
    new MagicQueueLoadTest();
  }

  final MagicQueue mq;
  final int write_size=57;
  final int writes_per_thread=10000000;
  final int write_threads=16;
  final int read_threads=4;
  final FusionInitiator fi;
  final Semaphore read_sem = new Semaphore(0);

  public MagicQueueLoadTest() throws Exception
  {
    mq = new MagicQueue(50000,256);

    fi = new FusionInitiator(read_threads);
    fi.start();

    double start_tm = System.currentTimeMillis();

    
    for(int i=0; i<read_threads; i++)
    {
      new QueueReader(i, i * read_threads / 256).start();
    }
    for(int i=0; i<write_threads; i++)
    {
      new QueueWriter().start();
    }

    read_sem.acquire(write_threads * writes_per_thread);

    double end_tm = System.currentTimeMillis();
    double sec = (end_tm - start_tm) / 1000.0;
    double items = write_threads * writes_per_thread;
    double rate = items / sec;

    DecimalFormat df = new DecimalFormat("0.00");

    System.out.println(String.format("%d items took %s seconds (%s/sec)", write_threads * writes_per_thread, df.format(sec), df.format(rate)));

    

  }

  public class QueueWriter extends Thread
  {
    public QueueWriter()
    {
      
    }

    public void run()
    {
      byte[] buff = new byte[write_size];
      Random rnd = new Random();
      for(long x=0; x<writes_per_thread; x++)
      {
        rnd.nextBytes(buff);
        int bucket = rnd.nextInt(256);
        ByteBuffer bb = mq.openWrite(bucket, write_size);
        bb.put(buff);

      }
      mq.flushFromLocal();
      
    }
  }

  public class QueueReader extends Thread
  {
    int task_number;
    int start;
    public QueueReader(int task_number, int start)
    {
      this.task_number = task_number;
      this.start = start;
      setDaemon(true);
    }

    public void run()
    {
      int b = start;
      while(true)
      {
        fi.taskWait(task_number);

        ByteBuffer bb = null;
        while((bb = mq.readBucket(b)) != null)
        {
          int items = bb.remaining() / write_size;
          read_sem.release(items);

        }



        fi.taskComplete(task_number);
        b = (b + 1) % 256;

        try
        {
        if (b == 0) {Thread.sleep(100);}
        }
        catch(Throwable t){}

      }

    }

  }
}
