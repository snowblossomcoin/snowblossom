package miner.test;

import org.junit.Test;
import org.junit.Assert;

import snowblossom.miner.FaQueue;
import snowblossom.miner.PartialWork;

public class FaQueueTest
{
  @Test
  public void testBasicEnqueueAndPoll()
  {
    FaQueue queue = new FaQueue(1000);

    Assert.assertEquals(0, queue.size());
    for(int i=0; i<10; i++)
    {
      queue.enqueue(new PartialWork( i % 6));
    }
    Assert.assertEquals(10, queue.size());
    for(int i=0; i<10; i++)
    {
      PartialWork pw = queue.poll();
      Assert.assertNotNull(pw);
    }
    Assert.assertEquals(0, queue.size());

  }

  @Test
  public void testMaxLimit()
  {
    FaQueue queue = new FaQueue(100);

    Assert.assertEquals(0, queue.size());
    for(int i=0; i<1000; i++)
    {
      queue.enqueue(new PartialWork(i % 6));
    }
    Assert.assertEquals(1000, queue.size());
    queue.tryPrune();
    Assert.assertEquals(100, queue.size());
  }
}
