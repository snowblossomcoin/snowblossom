package snowblossom.miner;

import java.util.Random;
import java.util.ArrayList;
import java.nio.ByteBuffer;

import snowblossom.lib.SnowMerkle;

public class BenchThread extends Thread
{
  private FieldSource fs;
  private Random rnd;
  private ArrayList<Integer> chunks;
  private ByteBuffer bb;
  private long accum=0;

  public BenchThread(FieldSource fs)
  {
    this.fs = fs;
    setDaemon(true);
    setName("BatchThread");
    rnd = new Random();
    bb = ByteBuffer.allocate(SnowMerkle.HASH_LEN);

    chunks = new ArrayList<>(fs.getHoldingSet());

  }

  public void run()
  {
    while(true)
    {
      try
      {
        read();     
      }
      catch(Throwable t)
      {
        t.printStackTrace();
      }
    }


  }

  private long getWordIndex()
  {
    long chunk = chunks.get(rnd.nextInt(chunks.size()));
    long x = chunk * fs.words_per_chunk;
    x += rnd.nextInt( (int) fs.words_per_chunk);
    return x;

  }

  private void read()
    throws Exception
  {
    if (fs instanceof BatchSource)
    {
      BatchSource bs = (BatchSource)fs;
      int n = bs.getSuggestedBatchSize();
      ArrayList<Long> lst = new ArrayList<Long>(n);
      for(int i=0; i<n; i++)
      {
        lst.add(getWordIndex());
      }
      bs.readWordsBulk(lst);

    }
    else
    {
      fs.readWord(getWordIndex(), bb);
      bb.clear();
      accum+=bb.getLong();
      accum+=bb.getLong();
      bb.clear();
      
      if (accum == 0L)
      {
        System.out.println("PARTY TIME");
      }

    }
    
    

  }



}
