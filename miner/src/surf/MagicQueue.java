package snowblossom.miner.surf;

import java.util.concurrent.LinkedBlockingQueue;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;


/**
 * Data optimization based on guesses about how NUMA works
 * and trying to keep things simple for the GC.  So probably all wrong.
 */
public class MagicQueue
{
  final LinkedBlockingQueue<ByteBuffer>[] global_buckets;
  final int max_chunk_size;
  final ThreadLocal<Map<Integer, ByteBuffer> > local_buff;
  
  
  public MagicQueue(int max_chunk_size, int bucket_count)
  {
    this.max_chunk_size = max_chunk_size;
    global_buckets = new LinkedBlockingQueue[bucket_count];
    for(int i=0; i<bucket_count; i++)
    {
      global_buckets[i] = new LinkedBlockingQueue<>();
    }

    local_buff = new ThreadLocal<Map<Integer, ByteBuffer>>() {
      @Override protected Map<Integer,ByteBuffer> initialValue() {
        return new HashMap<Integer, ByteBuffer>(bucket_count*2+1, 0.5f);  
      }
    };
  
  }

  public ByteBuffer openWrite(int bucket, int data_size)
  {
    Map<Integer, ByteBuffer> local = local_buff.get();
    if (local.containsKey(bucket))
    {
      if (local.get(bucket).remaining() >= data_size) return local.get(bucket);

      global_buckets[bucket].offer(local.get(bucket));
    }

    local.put(bucket, ByteBuffer.allocate(max_chunk_size));
    return local.get(bucket);

  }

  public ByteBuffer readBucket(int bucket)
  {
    return global_buckets[bucket].poll();
  }

  public void flushFromLocal()
  {
    for(Map.Entry<Integer,ByteBuffer> me : local_buff.get().entrySet())
    {
      int b = me.getKey();
      ByteBuffer bb = me.getValue();
      global_buckets[b].offer(bb);
    }

  }



}
