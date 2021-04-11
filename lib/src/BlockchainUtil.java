package snowblossom.lib;

import com.google.protobuf.ByteString;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Assert;
import snowblossom.lib.trie.ByteStringComparator;
import snowblossom.proto.*;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;



public class BlockchainUtil
{
  public static BigInteger targetBytesToBigInteger(ByteString bytes)
  {
    Assert.assertEquals(Globals.TARGET_LENGTH, bytes.size());

    return new BigInteger(1, bytes.toByteArray());

  }

  public static ByteString targetBigIntegerToBytes(BigInteger target)
  {
    ByteBuffer bb = ByteBuffer.allocate(Globals.TARGET_LENGTH);

    byte[] data = target.toByteArray();
    int zeros = Globals.TARGET_LENGTH - data.length;

    for (int i = 0; i < zeros; i++)
    {
      byte z = 0;
      bb.put(z);
    }
    bb.put(data);

    return ByteString.copyFrom(bb.array());
  }

  public static BigInteger getTargetForDiff(int n)
  {
    return BigInteger.ONE.shiftLeft(256 - n);
  }

  public static BigInteger readInteger(String s)
  {
    if (s.length() == 0) return BigInteger.ZERO;

    return new BigInteger(s);
  }

  public static BigInteger getWorkForSummary(BlockHeader header, BlockSummary prev_summary, NetworkParams params, List<ImportedBlock> imported_blocks)
  {
    try(TimeRecordAuto tra = TimeRecord.openAuto("BlockchainUtil.getWorkForSummary"))
    {
      BigInteger target = BlockchainUtil.targetBytesToBigInteger(header.getTarget());

      BigInteger slice = BigInteger.valueOf(1024L);

      // So a block at max target is 'slice' number of work units
      // A block at half the target (harder) is twice the number of slices.
      // Ok, so whoever wrote those two lines clearly had some concept in mind.
      // I wonder what the hell it was.

      
      BigInteger work_in_block = params.getMaxTarget().multiply(slice).divide(target);
      // add in work from imported blocks
      for(ImportedBlock ib : imported_blocks)
      {
        BigInteger import_target = BlockchainUtil.targetBytesToBigInteger(ib.getHeader().getTarget());
        work_in_block = work_in_block.add( params.getMaxTarget().multiply(slice).divide(import_target) );
      }

      // SIP2 - work is multipled by 4^activated_field.  That way, a higher field
      // takes precedence.
      BigInteger field_multipler = BigInteger.ONE.shiftLeft(prev_summary.getActivatedField() * 2);
      work_in_block = work_in_block.multiply(field_multipler);

      BigInteger prev_work_sum = BlockchainUtil.readInteger(prev_summary.getWorkSum());

      BigInteger worksum = prev_work_sum.add(work_in_block);
      return worksum;

    }

  }

  public static BlockSummary getNewSummary(BlockHeader header, BlockSummary prev_summary, NetworkParams params, long tx_count, long tx_body_sum, List<ImportedBlock> imported_blocks)
  {
    try(TimeRecordAuto tra = TimeRecord.openAuto("BlockchainUtil.getNewSummary"))
    {
      BlockSummary.Builder bs = BlockSummary.newBuilder();

      BigInteger target = BlockchainUtil.targetBytesToBigInteger(header.getTarget());

      bs.setTotalTransactions( prev_summary.getTotalTransactions() + tx_count );
      bs.setBlockTxCount( tx_count );

      // true if this is the first block of a fresh split
      boolean fresh_block_split=false;

      if (header.getVersion() == 2)
      { 
        // update the tx body running average
        long prev_tx_size_average;
        int prev_shard_len;

        if (prev_summary.getHeader().getShardId() != header.getShardId())
        { // shard split

          prev_tx_size_average = 0;
          prev_shard_len = 0;
          fresh_block_split = true;
        }
        else
        {
          prev_tx_size_average = prev_summary.getTxSizeAverage();
          prev_shard_len = prev_summary.getShardLength();
        }

        long prev_w = prev_tx_size_average * (1000L - params.getAvgWeight());
        long new_w = tx_body_sum * params.getAvgWeight();
        long new_avg = (prev_w + new_w) / 1000L;
        bs.setTxSizeAverage(new_avg);

        bs.setShardLength( prev_shard_len + 1 );
       

        bs.putAllImportedShards( prev_summary.getImportedShardsMap() );
        // put myself in
        bs.putImportedShards(header.getShardId(), header);
        for(ImportedBlock imb : imported_blocks)
        {
          int imp_shard = imb.getHeader().getShardId();
          bs.putImportedShards(imp_shard, imb.getHeader() );
        }

        // Import previous shard history and prune it down
        for(Map.Entry<Integer, BlockImportList> me : prev_summary.getShardHistoryMap().entrySet())
        {
          int shard = me.getKey();
          BlockImportList prev_hist = me.getValue();

          TreeMap<Integer, ByteString> height_map = new TreeMap<>();
          height_map.putAll( prev_hist.getHeightMapMap() );

          while(height_map.size() > 5 * params.getMaxShardSkewHeight() )
          {
            height_map.pollFirstEntry();
          }

          BlockImportList.Builder sh = BlockImportList.newBuilder();

          sh.putAllHeightMap( height_map );

          bs.putShardHistoryMap( shard, sh.build() );
        }

        // Read all headers and stick them into the shard histories
        LinkedList<BlockHeader> all_headers = new LinkedList<>();
        all_headers.add(header);
        for(ImportedBlock imb : imported_blocks)
        {
          int imp_shard = imb.getHeader().getShardId();
          all_headers.add( imb.getHeader() );
        }

        // Add all blocks into history
        for(BlockHeader bh : all_headers)
        {
          addBlockToHistory(bs, bh.getShardId(), bh.getBlockHeight(), new ChainHash(bh.getSnowHash()));
          for(Map.Entry<Integer, BlockImportList> me : bh.getShardImportMap().entrySet() )
          {
            int shard = me.getKey();
            BlockImportList bil = me.getValue();
            addBlockToHistory(bs, shard, bil);
          }
        }

      }

      BigInteger worksum = getWorkForSummary(header, prev_summary, params, imported_blocks);



      bs.setWorkSum(worksum.toString());

      long weight = params.getAvgWeight();
      long decay = 1000L - weight;
      BigInteger decay_bi = BigInteger.valueOf(decay);
      BigInteger weight_bi = BigInteger.valueOf(weight);

      long block_time;
      long prev_block_time;
      BigInteger prev_target_avg;

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
        prev_target_avg = BlockchainUtil.readInteger(prev_summary.getTargetAverage());

        if (fresh_block_split)
        { //With a split, the difficulty drops
          //We can make a quick trick block stack
          //We can make a quick trick clock stack
          prev_target_avg = prev_target_avg.multiply( BigInteger.valueOf(2L) );
        }
      }
      int field = prev_summary.getActivatedField();
      bs.setActivatedField( field );

      SnowFieldInfo next_field = params.getSnowFieldInfo(field + 1);
      if (next_field != null)
      {
        /*System.out.println(String.format("Field %d Target %f, activation %f", field+1,
          PowUtil.getDiffForTarget(prev_target_avg),
          PowUtil.getDiffForTarget(next_field.getActivationTarget())));*/
        if (prev_target_avg.compareTo(next_field.getActivationTarget()) <= 0)
        {
          bs.setActivatedField( field + 1 );
        }
      }

      bs.setBlocktimeAverageMs(  (prev_block_time * decay + block_time * weight) / 1000L );

      bs.setTargetAverage(
        prev_target_avg.multiply(decay_bi)
          .add(target.multiply(weight_bi))
          .divide(BigInteger.valueOf(1000L))
          .toString());


      bs.setHeader(header);

      return bs.build();
    }

  }

  private static void addBlockToHistory(BlockSummary.Builder bs, int shard, BlockImportList bil)
  {
    try(TimeRecordAuto tra = TimeRecord.openAuto("BlockchainUtil.addBlockToHistoryMap"))
    {
      if (!bs.getShardHistoryMap().containsKey(shard))
      {
        bs.putShardHistoryMap(shard, bil);
      }
      else
      {

        BlockImportList.Builder b = BlockImportList.newBuilder();
        
        b.mergeFrom(bs.getShardHistoryMap().get(shard));
        b.mergeFrom(bil);

        bs.putShardHistoryMap(shard, b.build());
      }
    }

  }



  private static void addBlockToHistory(BlockSummary.Builder bs, int shard, int height, ChainHash hash)
  {
    try(TimeRecordAuto tra = TimeRecord.openAuto("BlockchainUtil.addBlockToHistory"))
    {
      if (!bs.getShardHistoryMap().containsKey(shard))
      {
        bs.putShardHistoryMap(shard, BlockImportList.newBuilder().build());
      }

      BlockImportList.Builder b = BlockImportList.newBuilder();
      
      b.mergeFrom(bs.getShardHistoryMap().get(shard));
      b.putHeightMap(height, hash.getBytes());

      bs.putShardHistoryMap(shard, b.build());
    }

  }

  /**
   * return true iff b is a better block than a for head purposes
   */
  public static boolean isBetter(BlockSummary a, BlockSummary b)
  {
    if (a == null) return true;

    BigInteger a_work_sum = BlockchainUtil.readInteger(a.getWorkSum());
    BigInteger b_work_sum = BlockchainUtil.readInteger(b.getWorkSum());

    if (b_work_sum.compareTo(a_work_sum) > 0) return true;
    if (b_work_sum.compareTo(a_work_sum) < 0) return false;

    // tie breaker - oldest wins
    if (b.getHeader().getTimestamp() < a.getHeader().getTimestamp()) return true;
    if (a.getHeader().getTimestamp() > b.getHeader().getTimestamp()) return false;

    // tie breaker - lowest hash wins
    if (ByteStringComparator.compareStatic(b.getHeader().getSnowHash(), a.getHeader().getSnowHash()) < 0) return true;
    if (ByteStringComparator.compareStatic(b.getHeader().getSnowHash(), a.getHeader().getSnowHash()) > 0) return false;


    return false;

  }
  
  
}
