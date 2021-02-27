package snowblossom.lib;

import snowblossom.proto.BlockHeader;

public class ShardBlock implements Comparable<ShardBlock>
{
  private final int shard_id;
  private final int block_height;

  public ShardBlock(BlockHeader header)
  {
    shard_id = header.getShardId();
    block_height = header.getBlockHeight();
  }

  public ShardBlock(int shard_id, int block_height)
  {
    this.shard_id = shard_id;
    this.block_height = block_height;
  }

  public int getShardId(){return shard_id;}
  public int getBlockHeight(){return block_height;}

  @Override
  public int hashCode()
  {
    return shard_id * 1312311 + block_height;
  }

  @Override 
  public boolean equals(Object o)
  {
    if (o instanceof ShardBlock)
    {
      ShardBlock sb = (ShardBlock) o;
      if (getShardId() == sb.getShardId())
      if (getBlockHeight() == sb.getBlockHeight())
      {
        return true;
      }
    }
    return false;
  }

  @Override
  public int compareTo(ShardBlock sb)
  {
    if (getShardId() < sb.getShardId()) return -1;
    if (getShardId() > sb.getShardId()) return 1;
    if (getBlockHeight() < sb.getBlockHeight()) return -1;
    if (getBlockHeight() > sb.getBlockHeight()) return 1;
    return 0;
  }

  @Override
  public String toString()
  {
    return String.format("{shard=%d,h=%d}", getShardId(), getBlockHeight());
  }

}
