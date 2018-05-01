package snowblossom;

import snowblossom.proto.Block;
import snowblossom.proto.BlockHeader;
import snowblossom.proto.BlockSummary;
import snowblossom.proto.Transaction;
import snowblossom.proto.TransactionInner;
import snowblossom.proto.TransactionOutput;
import snowblossom.proto.CoinbaseExtras;
import java.util.Random;

import com.google.protobuf.ByteString;
import snowblossom.trie.HashUtils;

import java.util.LinkedList;
import java.util.List;

import java.security.MessageDigest;

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

  public Block getBlockTemplate(AddressSpecHash mine_to)
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

    byte[] hashbuff = new byte[32];


    long time = System.currentTimeMillis();
    long target = PowUtil.calcNextTarget(head, params, time);


    header_builder.setTimestamp(time);
    header_builder.setTarget(BlockchainUtil.targetLongToBytes(target));
    header_builder.setSnowField(0);


    try
    {

      UtxoUpdateBuffer utxo_buffer = new UtxoUpdateBuffer( node.getUtxoHashedTrie(), head.getHeader().getUtxoRootHash());
      List<Transaction> regular_transactions = getTransactions();
      long fee_sum = 0L;
      for(Transaction tx : regular_transactions)
      {
         fee_sum += Validation.deepTransactionCheck(tx, utxo_buffer);
      }

      Transaction coinbase = buildCoinbase( header_builder.getBlockHeight(), fee_sum, mine_to);
      Validation.deepTransactionCheck(coinbase, utxo_buffer);

      block_builder.addTransactions(coinbase);
      block_builder.addAllTransactions(regular_transactions);


      LinkedList<ChainHash> tx_list = new LinkedList<ChainHash>();
      for(Transaction tx : block_builder.getTransactionsList())
      {
        tx_list.add( new ChainHash(tx.getTxHash()));
      }

      header_builder.setMerkleRootHash( DigestUtil.getMerkleRootForTxList(tx_list).getBytes());
      header_builder.setUtxoRootHash( utxo_buffer.simulateUpdates());

      block_builder.setHeader(header_builder.build());
      return block_builder.build();
    }
    catch(ValidationException e)
    {
      throw new RuntimeException(e);
    }

  }

  private Transaction buildCoinbase(int height, long fees, AddressSpecHash mine_to)
  {
    Transaction.Builder tx = Transaction.newBuilder();

    TransactionInner.Builder inner = TransactionInner.newBuilder();
    inner.setVersion(1);
    inner.setIsCoinbase(true);

    CoinbaseExtras.Builder ext = CoinbaseExtras.newBuilder();

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

  private List<Transaction> getTransactions()
  {
    return new LinkedList<Transaction>();
  }


}
