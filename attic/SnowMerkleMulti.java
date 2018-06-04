package snowblossom;

import java.security.MessageDigest;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;

import lib.src.trie.HashUtils;
import lib.src.DaemonThreadFactory;
import lib.src.LRUCache;

import java.security.Security;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;
import java.util.HashMap;

import java.util.logging.Logger;


public class SnowMerkleMulti
{
  public static void main(String args[]) throws Exception
  {
    System.out.println(new SnowMerkleMulti(args[0]).getRootHashStr());
  }

  public static final int HASH_LEN = 16;
  public static final long HASH_LEN_LONG = HASH_LEN;

  public static final int THREADS=256;

  public static final int READ_BLOCK_SIZE = 1048576;

  private static final Logger logger = Logger.getLogger("SnowMerkleMulti");


  private FileChannel fc;
  private byte[] root_hash;

  private ThreadPoolExecutor exec;

  private ThreadLocal<MessageDigest> md_local;

  public SnowMerkleMulti(String file)
    throws Exception
  {
    Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

    RandomAccessFile raf = new RandomAccessFile(file, "r");
    fc = raf.getChannel();

    md_local = new ThreadLocal<MessageDigest>();


    exec=new ThreadPoolExecutor(THREADS, THREADS, 2, TimeUnit.DAYS,
                                new SynchronousQueue<Runnable>(),
                                new DaemonThreadFactory("SnowMerkle"), new ThreadPoolExecutor.CallerRunsPolicy());

    long total_len = new java.io.File(file).length();
    if (total_len % HASH_LEN_LONG != 0) throw new RuntimeException("Impedence mismatch - " + total_len);

    long blocks = total_len / HASH_LEN_LONG;

    root_hash = findTreeHash(0, blocks);

    raf.close();
    exec.shutdown();

  }
  public String getRootHashStr()
  {
    return HashUtils.getHexString(root_hash);
  }

  private byte[] findTreeHash(long start, long end)
    throws Exception
  {
    MessageDigest md = null;
    md = md_local.get();
    if (md == null)
    {
      md = MessageDigest.getInstance("Skein-256-128");
      md_local.set(md);
    }

    long dist = end - start;

    if (dist == 0) throw new RuntimeException("lol no");
    if (dist == 1)
    {
      byte[] buff = new byte[HASH_LEN];
      readHash(start, buff);
      return buff;
    }
    if (dist % 2 != 0) throw new RuntimeException("lolwut");


    long mid = (start + end ) / 2;

    Semaphore sem = new Semaphore(0);

    TreeHashRunnable left_run = new TreeHashRunnable(start, mid, sem);
    TreeHashRunnable right_run = new TreeHashRunnable(mid, end, sem);

    if (dist * HASH_LEN <=  2 * 1048576)
    {
      exec.execute(left_run);
      exec.execute(right_run);
    }
    else
    {
      left_run.run();
      right_run.run();
    }

    sem.acquire(2);

    byte[] left = left_run.getData();
    byte[] right = right_run.getData();

    md.update(left);
    md.update(right);

    byte[] hash = md.digest();
    md.reset();

    return hash;

  }

  public class TreeHashRunnable implements Runnable
  {
    private long start;
    private long end;
    private Semaphore sem;
    private byte[] data;

    public TreeHashRunnable(long start, long end, Semaphore sem)
    {
      this.start = start;
      this.end = end;
      this.sem = sem;
    }

    public void run()
    {
      try
      {
        data = findTreeHash(start, end);
        sem.release();
      }
      catch(Throwable t)
      {
        t.printStackTrace();
      }
    }
    public byte[] getData()
    {
      if (data == null) throw new RuntimeException("Data not ready");
      return data;
    } 
  }

  private void readHash(long hash_num, byte[] hash)
    throws Exception
  {
    long location = hash_num * HASH_LEN;
    int block =(int)(location / READ_BLOCK_SIZE);

    int location_in_block = (int) location - (block * READ_BLOCK_SIZE);

    byte[] block_data = getBlock(block);

    System.arraycopy(block_data, location_in_block, hash, 0, HASH_LEN);

  }

  LRUCache<Integer, ByteBuffer> block_cache = new LRUCache<>(16);
  HashMap<Integer, ByteBuffer> in_process_blocks = new HashMap<>();

  private byte[] getBlock(int block_num)
    throws Exception
  {
    byte[] b = null;

    ByteBuffer waitobj = null;

    boolean me_do_it = false;

    synchronized(block_cache)
    {
      waitobj = block_cache.get(block_num);
      if (waitobj != null) return waitobj.array();

      if (in_process_blocks.containsKey(block_num))
      {
        waitobj = in_process_blocks.get(block_num);
      }
      else
      {
        waitobj = ByteBuffer.allocate(READ_BLOCK_SIZE);
        in_process_blocks.put(block_num, waitobj);
        me_do_it=true;
      }
    }

    if (!me_do_it)
    {
      synchronized(waitobj)
      {
        while(waitobj.remaining() > 0)
        {
          waitobj.wait();
        }
      }
      return waitobj.array();
    }
    
    synchronized(waitobj)
    {
      logger.info("Reading block " + block_num);
      long position = (long)block_num * (long)READ_BLOCK_SIZE;
      while(waitobj.remaining() > 0)
      {
        fc.read(waitobj, position);
      }
 
    }

    synchronized(block_cache)
    {
      in_process_blocks.remove(block_num);
      block_cache.put(block_num, waitobj);
    }

    synchronized(waitobj)
    {
      waitobj.notifyAll();
    }

    return waitobj.array();

  }




}
