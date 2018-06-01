package snowblossom;

import com.google.protobuf.ByteString;
import snowblossom.proto.*;
import snowblossom.trie.HashUtils;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.LinkedList;
import java.util.List;

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

  public Block getBlockTemplate(AddressSpecHash mine_to, CoinbaseExtras extras)
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
         fee_sum += Validation.deepTransactionCheck(tx, utxo_buffer);
      }

      Transaction coinbase = buildCoinbase( header_builder.getBlockHeight(), fee_sum, mine_to, extras);
      Validation.deepTransactionCheck(coinbase, utxo_buffer);

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

  private Transaction buildCoinbase(int height, long fees, AddressSpecHash mine_to, CoinbaseExtras extras)
  {
    Transaction.Builder tx = Transaction.newBuilder();

    TransactionInner.Builder inner = TransactionInner.newBuilder();
    inner.setVersion(1);
    inner.setIsCoinbase(true);

    CoinbaseExtras.Builder ext = CoinbaseExtras.newBuilder();
    ext.mergeFrom(extras);

    if (height == 0)
    {
      ext.setRemarks(params.getBlockZeroRemark());
    }
    ext.setBlockHeight(height);

    inner.setCoinbaseExtras(ext.build());

    long total_reward = PowUtil.getBlockReward(params, height) + fees;

    inner.addOutputs( TransactionOutput.newBuilder()
      .setValue(total_reward)
      .setRecipientSpecHash(mine_to.getBytes())
      .build());

    ByteString inner_data = inner.build().toByteString();

		MessageDigest md_bc = DigestUtil.getMD();

		tx.setTxHash(ByteString.copyFrom(md_bc.digest(inner_data.toByteArray())));

    tx.setInnerData(inner_data);

    return tx.build();
  }

  private List<Transaction> getTransactions(ChainHash prev_utxo_root)
  {
    return node.getMemPool().getTransactionsForBlock(prev_utxo_root, Globals.MAX_BLOCK_SIZE);
  }


}
