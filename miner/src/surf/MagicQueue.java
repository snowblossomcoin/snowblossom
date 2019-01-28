package snowblossom.miner.surf;

import java.util.concurrent.LinkedBlockingQueue;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.LinkedList;


/**
 * Data optimization based on guesses about how NUMA works
 * and trying to keep things simple for the GC.  So probably all wrong.
 */
public class MagicQueue
{
  /**
   * Collection of ByteBuffers for each bucket, ready to be read
   */
  private final LinkedList<ByteBuffer>[] global_buckets;
  private final int max_chunk_size;
  private final int bucket_count;

  /**
   * Each thread accumulatedd data in this map before they are saved
   * to the global buckets.
   */
  //private final ThreadLocal<Map<Integer, ByteBuffer> > local_buff;
  private final ThreadLocal<ByteBuffer[] > local_buff;
  
  
  public MagicQueue(int max_chunk_size, int bucket_count)
  {
    this.max_chunk_size = max_chunk_size;
    this.bucket_count = bucket_count;

    global_buckets = new LinkedList[bucket_count];
    for(int i=0; i<bucket_count; i++)
    {
      global_buckets[i] = new LinkedList<>();
    }

    local_buff = new ThreadLocal<ByteBuffer[]>() {
      @Override protected ByteBuffer[] initialValue() {
        //return new HashMap<Integer, ByteBuffer>(bucket_count*2+1, 0.5f);  
        //return new TreeMap<Integer, ByteBuffer>();  
        return new ByteBuffer[bucket_count];

      }
    };
  
  }


  /**
   * returns a ByteBuffer that is ready to accepts writes up to data_sizee
   * as needed.  Might already have data in it.  Can only be used in this thread.
   * Might not get saved to the global bucket until flush is called.
   */
  public ByteBuffer openWrite(int bucket, int data_size)
  {
    ByteBuffer[] local = local_buff.get();
    if (local[bucket] != null)
    {
      if (local[bucket].remaining() >= data_size) return local[bucket];

      writeToBucket( bucket, local[bucket] );
    }

    local[bucket] = ByteBuffer.allocate(max_chunk_size);

    return local[bucket];

  }

  /**
   * @param data A byte buffer open for writes
   */
  private void writeToBucket(int bucket, ByteBuffer data)
  {
    LinkedList<ByteBuffer> lst = global_buckets[bucket];
    synchronized(lst)
    {
      ByteBuffer last = lst.peekLast();
      if ((last != null) && (last.remaining() >= data.position()))
      {
        data.flip();
        last.put(data);
      }
      else
      {
        lst.add(data);
      }
    }
  }

  /**
   * Returns null of a ByteBuffer with position 0 and limit set to how much data is there.
   * ready for reading.
   */

  public ByteBuffer readBucket(int bucket)
  {
    LinkedList<ByteBuffer> lst = global_buckets[bucket];
    synchronized(lst)
    {
      ByteBuffer bb = lst.poll();
      if (bb == null) return null;
      bb.flip();
      return bb;
    }
  }

  public void flushFromLocal()
  {
    
    ByteBuffer[] local = local_buff.get();
    for(int b=0; b<bucket_count; b++)
    {
      if (local[b] != null)
      {
        writeToBucket(b, local[b]);
        local[b]=null;
      }
    }

  }

  /** Might not clear all, some stuff might still be in ThreadLocal buffers */
  public void clearAll()
  {
    for(int i=0; i<bucket_count; i++)
    {
      LinkedList<ByteBuffer> lst = global_buckets[i];
      synchronized(lst)
      {
        lst.clear();
      }
    }

  }



}
