package snowblossom.miner;

import java.util.Random;
import java.security.MessageDigest;
import java.nio.ByteBuffer;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.mining.proto.*;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;

import java.util.Queue;
import org.junit.Assert;

import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.protobuf.ByteString;


public class BatchLayerWorkThread extends LayerWorkThread
{
	private static final Logger logger = Logger.getLogger("snowblossom.miner");
  //  256 = 590hk/s
  //  512 = 635kh/s 
  // 1024 = 650kh/s
  public static final int BATCH_SIZE=1024;

	public BatchLayerWorkThread(Arktika arktika, FieldSource fs, FaQueue queue, long total_words)
	{
    super(arktika, fs, queue, total_words);

	}

  @Override
	protected void runPass() throws Exception
	{
    LinkedList<PartialWork> pw_list = new LinkedList<>();

    queue.superPoll(BATCH_SIZE, pw_list);

    if (pw_list.size() < BATCH_SIZE)
    {
      WorkUnit wu = arktika.getWorkUnit();
      if (wu == null)
      {
        sleep(250);
        return;
      }
      int diff = BATCH_SIZE - pw_list.size();

      for(int x =0; x<diff; x++)
      {
        PartialWork pw = new PartialWork(wu, rnd, md, total_words);
        long next_word = pw.getNextWordIdx();
        int chunk = (int)(next_word / fs.words_per_chunk);
        if (fs.hasChunk(chunk))
        {
          pw_list.add(pw);
        }
        else
        {
          arktika.enqueue(chunk, pw);
        }
      }
      arktika.tryPruneAllQueues();
    }

    if (pw_list.size() > 0)
    {
      ArrayList<Long> words = new ArrayList<Long>();
      for(PartialWork pw : pw_list)
      {
        words.add(pw.getNextWordIdx());
      }
      BatchSource bs = (BatchSource) fs;
      List<ByteString> answers = bs.readWordsBulk(words);

      for(int i=0; i<pw_list.size(); i++)
      {
        PartialWork pw = pw_list.get(i);
        pw.doPass(answers.get(i).toByteArray(), md, total_words);
        processPw(pw);
      }
    }
	}

  @Override
  protected void processPw(PartialWork pw)
    throws Exception
  {
    if (pw.passes_done == Globals.POW_LOOK_PASSES)
    {
      Assert.assertNotNull(pw);
      Assert.assertNotNull(pw.context);
      Assert.assertNotNull(pw.wu);
		  if (PowUtil.lessThanTarget(pw.context, pw.wu.getReportTarget()))
	  	{
			  String str = HexUtil.getHexString(pw.context);
			  logger.info("Found passable solution: " + str);
			  submitWork(pw);
		  }
		  arktika.op_count.add(1L);
    }
    else
    {
      long next_word = pw.getNextWordIdx();
      int chunk = (int)(next_word / fs.words_per_chunk);
      arktika.enqueue(chunk, pw);
    }

  }

}

