package snowblossom;

import org.junit.Assert;

import snowblossom.proto.TransactionInput;
import snowblossom.proto.TransactionOutput;
import java.nio.ByteBuffer;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import snowblossom.trie.proto.TrieNode;

import java.util.Random;

/**
 * Simple class that acts as an easy way to make a transaction input
 * from an output or trie node or whatever */
public class TransactionBridge
{
  public final TransactionOutput out;
  public final TransactionInput in;
  public final long value;

  public TransactionBridge(TrieNode node)
  {
    Assert.assertTrue(node.getIsLeaf());

    try
    {

      out = TransactionOutput.parseFrom(node.getLeafData());

      ByteString key = node.getPrefix();

      ByteBuffer bb = ByteBuffer.wrap(key.toByteArray());

      byte[] address_hash = new byte[Globals.ADDRESS_SPEC_HASH_LEN];
      byte[] txid = new byte[Globals.BLOCKCHAIN_HASH_LEN];
      bb.get(address_hash);
      bb.get(txid);
      int out_idx = bb.getShort();

      in = TransactionInput.newBuilder()
        .setSpecHash( out.getRecipientSpecHash())
        .setSrcTxId( ByteString.copyFrom(txid))
        .setSrcTxOutIdx( out_idx)
        .build();
      value = out.getValue();

    }
    catch(InvalidProtocolBufferException e)
    {
      throw new RuntimeException(e);
    }
  }

  /**
   * create fake bridge for testing
   */
  protected TransactionBridge(AddressSpecHash hash, long value)
  {
    Random rnd = new Random();

    out = TransactionOutput.newBuilder()
      .setRecipientSpecHash(hash.getBytes())
      .setValue(value)
      .build();

    byte[] txid = new byte[Globals.BLOCKCHAIN_HASH_LEN];
    rnd.nextBytes(txid);

    in = TransactionInput.newBuilder()
      .setSpecHash(hash.getBytes())
      .setSrcTxId(ByteString.copyFrom(txid))
      .setSrcTxOutIdx(rnd.nextInt(100))
      .build();

    this.value = value;

    
  }


}
