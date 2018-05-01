package snowblossom;

import snowblossom.trie.HashedTrie;

import com.google.protobuf.ByteString;

import snowblossom.proto.TransactionInput;
import snowblossom.proto.TransactionOutput;

import java.nio.ByteBuffer;
import java.util.HashMap;

public class UtxoUpdateBuffer
{
  private HashedTrie trie;
  private ByteString utxo_root;

  private HashMap<ByteString, ByteString> updates;

  public UtxoUpdateBuffer(HashedTrie trie, ByteString utxo_root)
  {
    this.trie = trie;
    this.utxo_root = utxo_root;
    this.updates = new HashMap<>(512, 0.5f);
  }

  public void commitIfEqual(ByteString expected_hash)
    throws ValidationException
  {
    if (!trie.mergeIfNewRoot(utxo_root, updates, expected_hash))
    {
      throw new ValidationException("New utxo root does not match");
    }
  }
  public ByteString simulateUpdates()
  {
    return trie.simulateMerge(utxo_root, updates);
  }

  public TransactionOutput getOutputMatching(TransactionInput in)
  {
    ByteString key = getKey(new AddressSpecHash(in.getSpecHash()), 
      new ChainHash(in.getSrcTxId()), in.getSrcTxOutIdx());

    ByteString data;
    if (updates.containsKey(key))
    {
      data = updates.get(key);
    }
    else
    {
      data = trie.get(utxo_root, key);
    }

    if (data == null) return null;

    try
    {
      return TransactionOutput.parseFrom(data);
    }
    catch(com.google.protobuf.InvalidProtocolBufferException e)
    {
      throw new RuntimeException(e);
    }

  }

  public void useOutput(TransactionOutput out, ChainHash tx_id, int out_idx)
  {
    ByteString key = getKey(
      new AddressSpecHash(out.getRecipientSpecHash()),
      tx_id,
      out_idx);
    updates.put(key, null);

  }

  public void addOutput(TransactionOutput out, ChainHash tx_id, int out_idx)
  {
    ByteString key = getKey(
      new AddressSpecHash(out.getRecipientSpecHash()),
      tx_id,
      out_idx);
    updates.put(key, out.toByteString());
  }

  public static ByteString getKey(AddressSpecHash addr, ChainHash tx_id, int out_idx)
  {
    short s = (short) out_idx;
    ByteBuffer bb = ByteBuffer.allocate(2);
    bb.putShort(s);

    return addr.getBytes()
      .concat(tx_id.getBytes())
      .concat(ByteString.copyFrom(bb.array()));
  }


}
