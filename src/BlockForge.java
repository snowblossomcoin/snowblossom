package snowblossom;

import snowblossom.proto.Block;
import snowblossom.proto.BlockHeader;
import snowblossom.proto.BlockSummary;
import java.util.Random;

import com.google.protobuf.ByteString;

/**
 * This class creates new blocks for miners to work on
 */
public class BlockForge
{

  private SnowBlossomNode node;
  private NetworkParams params;
	public BlockForge(SnowBlossomNode node)
  {
    this.node = node;
    this.params = node.getParams();
  }

  public Block getBlockTemplate()
  {
    BlockSummary head = node.getBlockIngestor().getHead();

    Block.Builder block_builder = Block.newBuilder();

    BlockHeader.Builder header_builder = BlockHeader.newBuilder();

    header_builder.setVersion(1);

    if (head == null)
    {
      header_builder.setBlockHeight(1);
      header_builder.setPrevBlockHash(ChainHash.ZERO_HASH.getBytes());

      head = BlockSummary.newBuilder().build();

    }
    else
    {
      header_builder.setBlockHeight(head.getHeader().getBlockHeight() + 1);
      header_builder.setPrevBlockHash(head.getHeader().getSnowHash());

    }

    byte[] hashbuff = new byte[32];
    Random rnd = new Random();
    rnd.nextBytes(hashbuff); header_builder.setMerkleRootHash(ByteString.copyFrom(hashbuff));
    rnd.nextBytes(hashbuff); header_builder.setUtxoRootHash(ByteString.copyFrom(hashbuff));

    long time = System.currentTimeMillis();
    long target = PowUtil.calcNextTarget(head, params, time);


    header_builder.setTimestamp(time);
    header_builder.setTarget(BlockchainUtil.targetLongToBytes(target));
    header_builder.setSnowField(0);

    block_builder.setHeader(header_builder.build());

    return block_builder.build();


  }
}
