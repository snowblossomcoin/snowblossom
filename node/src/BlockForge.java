package snowblossom.node;

import com.google.protobuf.ByteString;
import snowblossom.proto.*;
import snowblossom.lib.*;
import snowblossom.lib.trie.HashUtils;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import org.junit.Assert;
import java.util.Random;

import com.google.common.collect.ImmutableList;

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

  public Block getBlockTemplate(SubscribeBlockTemplateRequest mine_to)
  {
    BlockSummary head = node.getBlockIngestor().getHead();

    Block.Builder block_builder = Block.newBuilder();

    BlockHeader.Builder header_builder = BlockHeader.newBuilder();

    header_builder.setVersion(1);

    if (head == null)
    {
      header_builder.setBlockHeight(0);
      header_builder.setPrevBlockHash(ChainHash.ZERO_HASH.getBytes());

      head = BlockSummary.newBuilder()
               .setHeader(BlockHeader.newBuilder().setUtxoRootHash( HashUtils.hashOfEmpty() ).build())
             .build();
    }
    else
    {
      header_builder.setBlockHeight(head.getHeader().getBlockHeight() + 1);
      header_builder.setPrevBlockHash(head.getHeader().getSnowHash());
    }


    long time = System.currentTimeMillis();
    BigInteger target = PowUtil.calcNextTarget(head, params, time);


    header_builder.setTimestamp(time);
    header_builder.setTarget(BlockchainUtil.targetBigIntegerToBytes(target));
    header_builder.setSnowField(head.getActivatedField());

    try
    {

      UtxoUpdateBuffer utxo_buffer = new UtxoUpdateBuffer( node.getUtxoHashedTrie(), 
        new ChainHash(head.getHeader().getUtxoRootHash()));
      List<Transaction> regular_transactions = getTransactions(new ChainHash(head.getHeader().getUtxoRootHash()));
      long fee_sum = 0L;
      for(Transaction tx : regular_transactions)
      {
         fee_sum += Validation.deepTransactionCheck(tx, utxo_buffer, header_builder.build(), params);
      }

      Transaction coinbase = buildCoinbase( header_builder.getBlockHeight(), fee_sum, mine_to);
      Validation.deepTransactionCheck(coinbase, utxo_buffer, header_builder.build(), params);

      block_builder.addTransactions(coinbase);
      block_builder.addAllTransactions(regular_transactions);


      LinkedList<ChainHash> tx_list = new LinkedList<ChainHash>();
      for(Transaction tx : block_builder.getTransactionsList())
      {
        tx_list.add( new ChainHash(tx.getTxHash()));
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

  private Transaction buildCoinbase(int height, long fees, SubscribeBlockTemplateRequest mine_to)
    throws ValidationException
  {
    Transaction.Builder tx = Transaction.newBuilder();

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

    inner.setCoinbaseExtras(ext.build());

    long total_reward = PowUtil.getBlockReward(params, height) + fees;

    inner.addAllOutputs( makeCoinbaseOutputs( params, total_reward, mine_to));


    ByteString inner_data = inner.build().toByteString();

		MessageDigest md_bc = DigestUtil.getMD();

		tx.setTxHash(ByteString.copyFrom(md_bc.digest(inner_data.toByteArray())));

    tx.setInnerData(inner_data);

    return tx.build();
  }

  public static List<TransactionOutput> makeCoinbaseOutputs(NetworkParams params, long total_reward, SubscribeBlockTemplateRequest req)
    throws ValidationException
  {
    if (req.getPayRewardToSpecHash().size() > 0)
    {
      AddressSpecHash addr = new AddressSpecHash(req.getPayRewardToSpecHash());

      return ImmutableList.of(
        TransactionOutput.newBuilder()
          .setValue(total_reward)
          .setRecipientSpecHash(addr.getBytes())
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
          .build());
    }

    return outs;

  }

  private List<Transaction> getTransactions(ChainHash prev_utxo_root)
  {
    return node.getMemPool().getTransactionsForBlock(prev_utxo_root, Globals.MAX_BLOCK_SIZE);
  }


}
