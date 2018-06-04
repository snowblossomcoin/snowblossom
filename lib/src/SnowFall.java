package snowblossom.lib;

import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/** 
 * Objective - create a deterministic large file.
 *
 * We want it hard to generate without having entire file. 
 * This does does that by multiple PASSES of jumping around and 
 * using existing data to inform new data and next jump (updating rng state)
 *
 * Hard to parameterize each page with the rng states to make
 * that page because the rng state is larger than the page size.
 *
 * Hard to quickly generate for a certain page in memory
 * because you need random access to entire file to build.
 */
public class SnowFall
{
  public static final int PASSES=7;

  // Page size for modern drives is probably 4k.
  // Experimentally, going below this produces much sadness.
  public static final int PAGESIZE=4*1024;
  public static final int MULTIPLICITY=128;

  public static final int SNOWMONSTER_PAGESIZE=PAGESIZE/4;
  public static final int SNOWMONSTER_COUNT= 268435456 / SNOWMONSTER_PAGESIZE; //want 256 mb

  // These two can be changed without changing the result
  public static final int threads = 128;

  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  public static void main(String args[]) throws Exception
  {
    if (args.length != 3)
    {
      System.out.println("SnowFall filename seed size_mb");
      System.exit(-10);
    }
    String filename = args[0];
    String seed = args[1];
    long size = Long.parseLong(args[2]) * 1048576L;
    new SnowFall(filename, seed, size);
  }

  private FileChannel snow_fc;
  private Queue<ByteBuffer> snow_monster;

  public SnowFall(String filename, String seed, long size)
    throws Exception
  {
    logger.info(String.format("Starting snowfall on %s with seed '%s' size %d, MULTIPLICITY %d", filename, seed, size, MULTIPLICITY));
    RandomAccessFile snow = new RandomAccessFile(filename, "rw");

    ThreadPoolExecutor exec = new ThreadPoolExecutor(threads, threads, 
      2, TimeUnit.DAYS, 
      new LinkedBlockingQueue<Runnable>(),
      new DaemonThreadFactory("SnowFall"));
    Semaphore syncsem = new Semaphore(0);

    snow_monster = new LinkedList<ByteBuffer>();

    snow.setLength(0);
    snow.setLength(size);

    snow_fc = snow.getChannel();

    PRNGStream rng_stream = new PRNGStream(seed);
    fillSnowMonster(rng_stream);
    long page_count = size / (long)PAGESIZE;
    
    { // First pass, just write some data to fill in the file
      // not at all secure here, because someone could save checkpoints of the
      // PRNG state to quickly regenerate parts of the file
      snow.seek(0);
      byte[] w_buff=new byte[1048576];
      long mb_count = size / 1048576;
      for(long w=0; w<mb_count; w++)
      {
        rng_stream.nextBytes(w_buff);
        writeFully(w * 1048576L, w_buff);
        rng_stream.mixBytes(snow_monster.poll().array());
        fillSnowMonster(rng_stream);
        if (w % 128 == 0)
        {
          logger.info(String.format("Initial write of %s - %d mb done", filename, w));

        }
      }
    }
    fillSnowMonster(rng_stream);

    snow_fc.force(true);

    logger.info("Initial write complete");

    // In this section, we:
    //  - use our prng to pick a page
    //  - read that page
    //  - rng generate a new page
    //  - update rnd with data from existing page
    //  - xor new page with existing page
    //  - write that new xored page
    // So to make the new page, you need
    //  - the rng state (~5k)
    //  - the existing page (4k)

    byte[][] w_buff=new byte[MULTIPLICITY][PAGESIZE];

    // Passes controls how many writes we do.
    // Each page will be written on average 'PASSES' times
    // This of course means that we can't garantee that any
    // given page will be rewritten at all
    long writes = size * PASSES / (long)PAGESIZE / MULTIPLICITY;
    byte[][] loc_data = new byte[MULTIPLICITY][8];
    byte[][] existing = new byte[MULTIPLICITY][PAGESIZE];
    long seek[] = new long[MULTIPLICITY];

    long start_time = System.currentTimeMillis();
    long last_report = System.currentTimeMillis();

    for(long w=0; w<writes; w++)
    {
      if ((last_report + 10000L < System.currentTimeMillis()) || (w+1 == writes))
      {
        double delta_w = w * MULTIPLICITY;
        double ms = System.currentTimeMillis() - start_time;
        double sec = ms / 1000.0;
        double rate = delta_w / sec;
        double runtime_estimate = (double)writes * MULTIPLICITY / rate;
        double runtime_est_hours = runtime_estimate/3600.0;
        double per_comp = (double)w / (double)writes;
        DecimalFormat df = new DecimalFormat("0.00");

        logger.info(String.format("Generating snow field at %s writes per second.  Estimated total runtime is %s hours. %s complete.",
          df.format(rate),
          df.format(runtime_est_hours),
          df.format(per_comp)));
        last_report = System.currentTimeMillis();
      }

      for(int m=0; m<MULTIPLICITY; m++)
      {
        rng_stream.mixBytes(snow_monster.poll().array());
        rng_stream.nextBytes(w_buff[m]);
        rng_stream.nextBytes(loc_data[m]);
      }
      fillSnowMonster(rng_stream);

      HashSet<Long> location_set = new HashSet<Long>(MULTIPLICITY*2+1, 0.8f);

      // Select and seek to a page
      for(int m=0; m<MULTIPLICITY; m++)
      {
        BigInteger loc_big = new BigInteger(1, loc_data[m]);
        long pos = loc_big.mod( BigInteger.valueOf(page_count) ).longValue();

        while(location_set.contains(pos))
        {
          pos = (pos + 1) % page_count;
        }
        location_set.add(pos);
        seek[m] = pos * (long)PAGESIZE;

        final long seek_local = seek[m];
        final byte existing_local[] = existing[m];

        // read existing page
        exec.execute(new Runnable(){
          public void run()
          {
            readFully(seek_local, existing_local);
            syncsem.release();
          }
        });
      }
      syncsem.acquire(MULTIPLICITY);

      // mix page into rng
      for(int m=0; m<MULTIPLICITY; m++)
      {
        rng_stream.mixBytes(existing[m]);

        // xor
        for(int i=0; i<PAGESIZE; i++)
        {
          w_buff[m][i] ^= existing[m][i];
        }
        
        final long seek_local = seek[m];
        final byte newpage_local[] = w_buff[m];

        // save page back
        exec.execute(new Runnable(){
          public void run()
          {
            writeFully(seek_local, newpage_local);
            syncsem.release();
          }
        });
        rng_stream.mixBytes(newpage_local);
      }
      syncsem.acquire(MULTIPLICITY);

    }

    snow_fc.force(true);
    snow_fc.close();

    snow.close();
    exec.shutdown();
  }

  private void fillSnowMonster(PRNGStream rng)
  {
    while(snow_monster.size() < SNOWMONSTER_COUNT)
    {
      ByteBuffer bb = ByteBuffer.allocate(SNOWMONSTER_PAGESIZE);
      rng.nextBytes(bb.array());
      snow_monster.add(bb);
    }
  }

  private void readFully(long position, byte[] buff)
  {
    long seek = (position / PAGESIZE);
    try
    {
      ByteBuffer bb = ByteBuffer.wrap(buff);

      int r = 0;
      while(bb.remaining() > 0)
      {
        r += snow_fc.read(bb, position);
      }
    }
    catch(java.io.IOException e)
    {
      throw new RuntimeException(e);
    }
  }

  private void writeFully(long position, byte[] buff)
  {
    long seek = (position / PAGESIZE);
    try
    {
      ByteBuffer bb = ByteBuffer.wrap(buff);

      int r = 0;
      while(bb.remaining() > 0)
      {
        r += snow_fc.write(bb, position);
      }
    }
    catch(java.io.IOException e)
    {
      throw new RuntimeException(e);
    }
  }


}
