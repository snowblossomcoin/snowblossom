package snowblossom.miner;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import duckutil.MultiAtomicLong;

import snowblossom.lib.Globals;
import java.util.concurrent.Semaphore;

public class FaQueue
{

  public static final int SLOTS=Globals.POW_LOOK_PASSES;
  private ConcurrentLinkedQueue<PartialWork>[] queues; 
  private MultiAtomicLong[] counts;
  public final int max_elements;

  public FaQueue(int max_elements)
  {
    this.max_elements = max_elements;
    queues = new ConcurrentLinkedQueue[SLOTS];
    counts = new MultiAtomicLong[SLOTS];
    for(int i=0; i<SLOTS; i++)
    {
      queues[i] = new ConcurrentLinkedQueue<>();
      counts[i] = new MultiAtomicLong();
    }
  }

  public void enqueue(PartialWork pw)
  {
    int slot = pw.passes_done;
    queues[slot].add(pw);
    counts[slot].add(1L);
  }

  /**
   * There is some error to this number due to races during a clear
   * but it will be reset on the next clear so it shouldn't drift far
   */
  public int size()
  {
    int sz = 0;
    for(int i=0; i<SLOTS; i++) sz += counts[i].sum();
    return sz;
  }

  /**
   * it would be best for this to be run only by one thread at once
   */
  public void prune()
  {
    int sz = size();
    if (sz > max_elements)
    {
      int diff = sz - max_elements;
      for(int i=0; i<SLOTS; i++)
      {
        while(diff > 0)
        {
          PartialWork pw = queues[i].poll();
          if (pw == null) break;
          diff--;
          counts[i].add(-1L);
        }
      }

    }
  }

  private final Semaphore prune_sem = new Semaphore(1);
  public void tryPrune()
  {
    if (prune_sem.tryAcquire(1))
    {
      prune();
      prune_sem.release();
    }

  }

  public void superPoll(int max, List<PartialWork> lst)
  {
    int got = 0;
    for(int i=SLOTS-1; i>=0; i--)
    {
      while(got < max)
      {
        PartialWork pw = queues[i].poll();
        if (pw != null)
        {
          counts[i].add(-1L);
          lst.add(pw);
          got++;
        }
        else
        {
          break;
        }
      }
    }
  }

  public PartialWork poll()
  {
    for(int i=SLOTS-1; i>=0; i--)
    {
      PartialWork pw = queues[i].poll();
      if (pw != null)
      {
        counts[i].add(-1L);
        return pw;
      }
    }
    return null;
  }

  public void clear()
  {
    for(int i=0; i<SLOTS; i++)
    {
      queues[i].clear();
      counts[i].sumAndReset();
    }
  }

}
