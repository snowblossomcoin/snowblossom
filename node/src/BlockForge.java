package snowblossom.node;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.junit.Assert;
import snowblossom.lib.*;
import snowblossom.lib.trie.HashUtils;
import snowblossom.proto.*;

/**
 * This class creates new blocks for miners to work on
 */
public class BlockForge
{

  private SnowBlossomNode node;
  private NetworkParams params;
  private final int shard_id;
  
  public BlockForge(SnowBlossomNode node, int shard_id)
  {
    this.node = node;
    this.params = node.getParams();
    this.shard_id = shard_id;
  }

  public Block getBlockTemplate(SubscribeBlockTemplateRequest mine_to)
  {
    BlockSummary head = node.getBlockIngestor(shard_id).getHead();

    Block.Builder block_builder = Block.newBuilder();

    BlockHeader.Builder header_builder = BlockHeader.newBuilder();

    
    header_builder.setVersion(1);
    header_builder.setShardId(shard_id);

    ChainHash prev_utxo_root = null;

    if (head == null)
    {
      header_builder.setBlockHeight(0);
      header_builder.setPrevBlockHash(ChainHash.ZERO_HASH.getBytes());

      prev_utxo_root = new ChainHash( HashUtils.hashOfEmpty() );

      head = BlockSummary.newBuilder()
               .setHeader(BlockHeader.newBuilder().setUtxoRootHash( HashUtils.hashOfEmpty() ).build())
             .build();
      if (shard_id != 0)
      {
        int parent_shard = ShardUtil.getShardParentId(shard_id);
        head = node.getBlockIngestor(parent_shard).getHead();
        if (head == null) return null;
        
        header_builder.setVersion(2);
        header_builder.setBlockHeight(head.getHeader().getBlockHeight() + 1);
        header_builder.setPrevBlockHash(head.getHeader().getSnowHash());

        if (ShardUtil.getInheritSet(shard_id).contains(parent_shard))
        {
      
          prev_utxo_root = new ChainHash(head.getHeader().getUtxoRootHash());
        }
        else
        {
          prev_utxo_root = new ChainHash( HashUtils.hashOfEmpty() );
        }
      }
    }
    else
    {
      if (ShardUtil.shardSplit(head, params))
      {
        // Can't mine on this shard any more
        return null;
      }

      prev_utxo_root = new ChainHash(head.getHeader().getUtxoRootHash());

     
      header_builder.setBlockHeight(head.getHeader().getBlockHeight() + 1);
      header_builder.setPrevBlockHash(head.getHeader().getSnowHash());
      if (header_builder.getBlockHeight() >= params.getActivationHeightShards())
      {
        header_builder.setVersion(2);
      }
    }


    long time = System.currentTimeMillis();
    BigInteger target = PowUtil.calcNextTarget(head, params, time);


    header_builder.setTimestamp(time);
    header_builder.setTarget(BlockchainUtil.targetBigIntegerToBytes(target));
    header_builder.setSnowField(head.getActivatedField());

    // TODO Select shard ID

    try
    {

      UtxoUpdateBuffer utxo_buffer = new UtxoUpdateBuffer( node.getUtxoHashedTrie(), 
        prev_utxo_root);
      List<Transaction> regular_transactions = getTransactions(new ChainHash(head.getHeader().getUtxoRootHash()));
      long fee_sum = 0L;

      Set<Integer> shard_cover_set = ShardUtil.getCoverSet(header_builder.getShardId(), params);
      Map<Integer, UtxoUpdateBuffer> export_utxo_buffer = new TreeMap<>();

      for(Transaction tx : regular_transactions)
      {
         fee_sum += Validation.deepTransactionCheck(tx, utxo_buffer, header_builder.build(), params,
          shard_cover_set, export_utxo_buffer);
      }

      Transaction coinbase = buildCoinbase( header_builder.build(), fee_sum, mine_to);
      Validation.deepTransactionCheck(coinbase, utxo_buffer, header_builder.build(), params,
        shard_cover_set, export_utxo_buffer);

      block_builder.addTransactions(coinbase);
      block_builder.addAllTransactions(regular_transactions);

      // TODO Save export utxo data


      int tx_size_total = 0;
      LinkedList<ChainHash> tx_list = new LinkedList<ChainHash>();
      for(Transaction tx : block_builder.getTransactionsList())
      {
        tx_list.add( new ChainHash(tx.getTxHash()));
        tx_size_total += tx.getInnerData().size() + tx.getTxHash().size();
      }

      if (header_builder.getVersion() == 2)
      {
        header_builder.setTxDataSizeSum(tx_size_total);
      }

      header_builder.setMerkleRootHash( DigestUtil.getMerkleRootForTxList(tx_list).getBytes());
      header_builder.setUtxoRootHash( utxo_buffer.simulateUpdates().getBytes());

      block_builder.setHeader(header_builder.build());
      return block_builder.build();
    }
    catch(ValidationException e)
    {
      throw new RuntimeException(e);
    }

  }

  private Transaction buildCoinbase(BlockHeader header, long fees, SubscribeBlockTemplateRequest mine_to)
    throws ValidationException
  {
    Transaction.Builder tx = Transaction.newBuilder();
    int height = header.getBlockHeight();

    TransactionInner.Builder inner = TransactionInner.newBuilder();
    inner.setVersion(1);
    inner.setIsCoinbase(true);

    CoinbaseExtras.Builder ext = CoinbaseExtras.newBuilder();
    ext.mergeFrom(mine_to.getExtras());

    if (height == 0)
    {
      ext.setRemarks(params.getBlockZeroRemark());
    }
    ext.setBlockHeight(height);
    ext.setShardId(header.getShardId());

    inner.setCoinbaseExtras(ext.build());


    //long total_reward = PowUtil.getBlockReward(params, height) + fees;
    long total_reward = ShardUtil.getBlockReward(params, header) + fees;
    

    inner.addAllOutputs( makeCoinbaseOutputs( params, total_reward, mine_to, shard_id));


    ByteString inner_data = inner.build().toByteString();

    MessageDigest md_bc = DigestUtil.getMD();

    tx.setTxHash(ByteString.copyFrom(md_bc.digest(inner_data.toByteArray())));

    tx.setInnerData(inner_data);

    return tx.build();
  }

  public static List<TransactionOutput> makeCoinbaseOutputs(NetworkParams params, long total_reward, SubscribeBlockTemplateRequest req, int target_shard)
    throws ValidationException
  {
    if (req.getPayRewardToSpecHash().size() > 0)
    {
      AddressSpecHash addr = new AddressSpecHash(req.getPayRewardToSpecHash());

      return ImmutableList.of(
        TransactionOutput.newBuilder()
          .setValue(total_reward)
          .setRecipientSpecHash(addr.getBytes())
          .setTargetShard(target_shard)
          .build());
    }
    double total_weight = 0.0;
    Map<String, Double> ratio_input_map = req.getPayRatios();

    for(Double d : ratio_input_map.values())
    {
      total_weight += d;
    }

    Map<String, Long> amount_output_map = new TreeMap<>();
    ArrayList<String> names_with_funds = new ArrayList<>();
    double total_reward_dbl = total_reward;
    long spent_reward = 0;
    for(Map.Entry<String, Double> me : ratio_input_map.entrySet())
    {
      String s = me.getKey();
      double ratio = me.getValue();

      long val = (long)(total_reward_dbl * ratio / total_weight);
      if (val > 0)
      {
        spent_reward +=val;
        names_with_funds.add(s);
        amount_output_map.put(s,val);
      }
    }

    long diff = total_reward - spent_reward;
    Assert.assertTrue(spent_reward <= total_reward);

    if (diff != 0)
    {
      Random rnd = new Random();
      String name = names_with_funds.get(rnd.nextInt(names_with_funds.size()));
      amount_output_map.put(name, amount_output_map.get(name) + diff);

    }

    LinkedList<TransactionOutput> outs = new LinkedList<>();
    for(Map.Entry<String, Long> me : amount_output_map.entrySet())
    {
      AddressSpecHash addr = AddressUtil.getHashForAddress(params.getAddressPrefix(), me.getKey());
      outs.add( 
        TransactionOutput.newBuilder()
          .setValue(me.getValue())
          .setRecipientSpecHash(addr.getBytes())
          .setTargetShard(target_shard)
          .build());
    }

    return outs;

  }

  private List<Transaction> getTransactions(ChainHash prev_utxo_root)
  {
    return node.getMemPool(shard_id).getTransactionsForBlock(prev_utxo_root, node.getParams().getMaxBlockSize());
  }


}
