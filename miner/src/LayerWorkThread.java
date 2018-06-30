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


import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.protobuf.ByteString;


public class LayerWorkThread extends Thread
{
	private static final Logger logger = Logger.getLogger("snowblossom.miner");

	Random rnd;
	MessageDigest md = DigestUtil.getMD();

	FieldSource fs;
	Arktika arktika;
  Queue<PartialWork> queue;
  long total_words;

	public LayerWorkThread(Arktika arktika, FieldSource fs, Queue<PartialWork> queue, long total_words)
	{
		this.fs = fs;
		this.arktika = arktika;
    this.queue = queue;
    this.total_words = total_words;
		setName("LayerWorkThread(" + fs.toString() + ")");
		setDaemon(true);
		rnd = new Random();

	}

	private void runPass() throws Exception
	{
    PartialWork pw = null;
    synchronized(queue){pw=queue.poll();}
    if (pw == null)
    {
      WorkUnit wu = arktika.last_work_unit;
      if (wu == null)
      {
        sleep(250);
        return;
      }
      pw = new PartialWork(wu, rnd, md, total_words);
    }
    else
    {
      pw.doPass(fs, md, total_words);
    }
    processPw(pw);
	}

  private void processPw(PartialWork pw)
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
		  arktika.op_count.getAndIncrement();
    }
    else
    {
      long next_word = pw.getNextWordIdx();
      int chunk = (int)(next_word / fs.words_per_chunk);
      if (fs.skipQueueOnRehit() && (fs.hasChunk(chunk)))
      { 
        pw.doPass(fs, md, total_words);
        processPw(pw);
      }
      else
      {
        arktika.enqueue(chunk, pw);
      }
    }

  }

	private void submitWork(PartialWork pw) throws Exception
	{


    WorkUnit wu = pw.wu;
		byte[] first_hash = PowUtil.hashHeaderBits(wu.getHeader(), pw.nonce);
		byte[] context = first_hash;



		BlockHeader.Builder header = BlockHeader.newBuilder();
		header.mergeFrom(wu.getHeader());
		header.setNonce(ByteString.copyFrom(pw.nonce));

    byte[] word_buff = new byte[SnowMerkle.HASH_LEN];
    ByteBuffer word_bb = ByteBuffer.wrap(word_buff);
    
		for (int pass = 0; pass < Globals.POW_LOOK_PASSES; pass++)
		{
			word_bb.clear();
			long word_idx = PowUtil.getNextSnowFieldIndex(context, total_words);

			arktika.composit_source.readWord(word_idx, word_bb);
			SnowPowProof proof = ProofGen.getProof(arktika.composit_source, arktika.deck_source, word_idx, total_words);
			header.addPowProof(proof);
			context = PowUtil.getNextContext(context, word_buff);
		}

		byte[] found_hash = context;

		header.setSnowHash(ByteString.copyFrom(found_hash));

		WorkSubmitRequest.Builder req = WorkSubmitRequest.newBuilder();
		req.setWorkId(wu.getWorkId());
		req.setHeader(header.build());
		
		SubmitReply reply = arktika.blockingStub.submitWork( req.build());
		
		if (PowUtil.lessThanTarget(found_hash, header.getTarget()))
		{
			arktika.share_block_count.getAndIncrement();
		}
		logger.info("Work submit: " + reply);
		arktika.share_submit_count.getAndIncrement();
		if (!reply.getSuccess())
		{
			arktika.share_reject_count.getAndIncrement();
		}

	}


	public void run()
	{
		while (!arktika.isTerminated())
		{
			boolean err = false;
			try (TimeRecordAuto tra = TimeRecord.openAuto("MinerThread.runPass"))
			{
				runPass();
			}
			catch (Throwable t)
			{
				err = true;
				logger.warning("Error: " + t);
        t.printStackTrace();
			}

			if (err)
			{

				try (TimeRecordAuto tra = TimeRecord.openAuto("MinerThread.errorSleep"))
				{
					Thread.sleep(5000);
				}
				catch (Throwable t)
				{
				}
			}
		}
	}
}

