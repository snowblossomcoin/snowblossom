package snowblossom;

import com.google.protobuf.ByteString;

import snowblossom.proto.Block;
import snowblossom.proto.BlockSummary;
import snowblossom.proto.BlockHeader;

import snowblossom.trie.HashUtils;

import snowblossom.db.DB;
import org.junit.Assert;

/**
 * This class takes in new blocks, validates them and stores them in the db.
 * In appropritate, updates the tip of the chain.
 */
public class BlockIngestor
{
  private SnowBlossomNode node;
  private DB db;
  private NetworkParams params;
  
  private volatile BlockSummary chainhead;

  public BlockIngestor(SnowBlossomNode node)
  {
    this.node = node;
    this.db = node.getDB();
    this.params = node.getParams();

    chainhead = db.getBlockSummaryMap().get("head");
  }

  public boolean ingestBlock(Block blk)
    throws ValidationException
  {
    Validation.checkBlockBasics(node.getParams(), blk, true);

    ChainHash blockhash = new ChainHash(blk.getHeader().getSnowHash());

    if (db.getBlockSummaryMap().containsKey(blockhash.toString() ))
    {
      return false;
    }

    ChainHash prevblock = new ChainHash(blk.getHeader().getPrevBlockHash());

    BlockSummary prev_summary;
    if (prevblock.equals(ChainHash.ZERO_HASH))
    {
      prev_summary = BlockSummary.newBuilder()
          .setHeader(BlockHeader.newBuilder().setUtxoRootHash( HashUtils.hashOfEmpty() ).build())
        .build();
    }
    else
    {
      prev_summary = db.getBlockSummaryMap().get( prevblock.toString() );
    }

    if (prev_summary == null)
    {
      return false;
    }

    // TODO - deeper validation here
    BlockSummary summary = getNewSummary(blk.getHeader(), prev_summary, node.getParams());

    Validation.deepBlockValidation(node, blk, prev_summary);

    db.getBlockMap().put( blockhash.toString(), blk);
    db.getBlockSummaryMap().put( blockhash.toString(), summary);

    if ((chainhead == null) || (summary.getWorkSum() > chainhead.getWorkSum()))
    {
      chainhead = summary;
      db.getBlockSummaryMap().put("head", summary);
    }
    return true;

  }

  public BlockSummary getHead()
  {
    return chainhead;
  }

  public static BlockSummary getNewSummary(BlockHeader header, BlockSummary prev_summary, NetworkParams params)
  {
    BlockSummary.Builder bs = BlockSummary.newBuilder();

    long target = BlockchainUtil.targetBytesToLong(header.getTarget());

    long work_in_block = params.getMaxTarget() * 128L / (target);
    Assert.assertTrue(work_in_block >= 128L);
    long worksum = prev_summary.getWorkSum() + work_in_block;

    bs.setWorkSum(worksum);

    long weight = params.getAvgWeight();
    long decay = 1000L - weight;

    long block_time;
    long prev_block_time;
    long prev_target_avg;

    if (prev_summary.getHeader().getTimestamp() == 0)
    { // first block, just pick a time
      block_time = params.getBlockTimeTarget();
      prev_block_time = params.getBlockTimeTarget();
      prev_target_avg = params.getMaxTarget();
    }
    else
    {
      block_time = header.getTimestamp() - prev_summary.getHeader().getTimestamp();
      prev_block_time = prev_summary.getBlocktimeAverageMs();
      prev_target_avg = prev_summary.getTargetAverage();
    }

    bs.setBlocktimeAverageMs(  (prev_block_time * decay + block_time * weight) / 1000L );

    bs.setTargetAverage( (prev_target_avg * decay + target * weight) / 1000L );

    bs.setHeader(header);
    
    return bs.build();

  }

}
