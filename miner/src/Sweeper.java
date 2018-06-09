package snowblossom.miner;

import snowblossom.proto.Block;

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The idea is that this reads the current snow field
 * to trigger the OS to bring it into cache.
 * In practice, this seems to do nothing.
 */
public class Sweeper extends Thread
{
  private SnowBlossomMiner miner;
  private SnowMerkleProof merkle_proof;
  private int proof_field;

	public Sweeper(SnowBlossomMiner miner)
  {
    this.miner = miner;

		setName("Sweeper");
		setDaemon(true);

  }



	private static final Logger logger = Logger.getLogger("snowblossom.miner");
	public void run()
	{
    while(true)
    {	
      try
      {
			  runPass();
      }
      catch(Throwable t)
      {
        logger.log(Level.WARNING, "Sweeper exception: " + t);
      }   
		}
	}

	private void runPass()
    throws Exception
	{
		Block b = miner.getBlockTemplate();
		if (b == null)
		{
			Thread.sleep(1000);
			return;
		}
		if ((merkle_proof == null) || (proof_field != b.getHeader().getSnowField()))
		{
			merkle_proof = miner.getFieldScan().getSingleUserFieldProof(b.getHeader().getSnowField());
			proof_field = b.getHeader().getSnowField();
		}
    if (merkle_proof==null)
    {
      Thread.sleep(30000);
      return;
    }
		long blocks = merkle_proof.getLength() / SnowMerkleProof.MEM_BLOCK;
		for(long b_no = 0; b_no < blocks; b_no++)
		{
		  ByteBuffer bb = ByteBuffer.allocate(SnowMerkleProof.MEM_BLOCK);
			long offset = b_no * SnowMerkleProof.MEM_BLOCK;
			merkle_proof.readChunk(offset, bb);
		}
    logger.info("Sweeper completed pass");
    Thread.sleep(30000);

	}

}

