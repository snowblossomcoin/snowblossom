package snowblossom.lib;

import com.google.protobuf.ByteString;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import snowblossom.lib.trie.HashUtils;
import snowblossom.lib.trie.HashedTrie;
import snowblossom.proto.TransactionInput;
import snowblossom.proto.TransactionOutput;
import snowblossom.proto.TxOutPoint;
import snowblossom.proto.ImportedOutputList;
import snowblossom.proto.ImportedOutput;

public class UtxoUpdateBuffer
{
  private HashedTrie trie;
  private ChainHash utxo_root;

  private HashMap<ByteString, ByteString> updates;

  public static final ChainHash EMPTY = new ChainHash(HashUtils.hashOfEmpty());

  public UtxoUpdateBuffer(HashedTrie trie, ChainHash utxo_root)
  {
    this.trie = trie;
    this.utxo_root = utxo_root;
    this.updates = new HashMap<>(512, 0.5f);
  }

  public UtxoUpdateBuffer deepCopy()
  {
    UtxoUpdateBuffer n = new UtxoUpdateBuffer(trie, utxo_root);
    n.updates.putAll(this.updates);

    return n;
  }

  public void commitIfEqual(ByteString expected_hash)
    throws ValidationException
  {
    if (!trie.mergeIfNewRoot(utxo_root.getBytes(), updates, expected_hash))
    {
      throw new ValidationException("New utxo root does not match");
    }
  }
  public ChainHash simulateUpdates()
  {
    return new ChainHash(trie.simulateMerge(utxo_root.getBytes(), updates));
  }

  /** Generally don't want to do this, used only in test */
  public ChainHash commit()
  {
    return new ChainHash(trie.mergeBatch(utxo_root.getBytes(), updates));
  }

  public TransactionOutput getOutputMatching(TransactionInput in)
  {
    ByteString key = getKey(in);

    ByteString data;
    if (updates.containsKey(key))
    {
      data = updates.get(key);
    }
    else
    {
      data = trie.getLeafData(utxo_root.getBytes(), key);
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

  /**
   * Determine if outpoint is still valid
   * Does not check to see if recorded output matches given output
   */
  public boolean checkOutpointExists(TxOutPoint tx_out_point)
  {
    ByteString key = getKey(
      new AddressSpecHash(tx_out_point.getOut().getRecipientSpecHash()),
      new ChainHash(tx_out_point.getTxHash()),
      tx_out_point.getOutIdx());

    ByteString data = trie.getLeafData(utxo_root.getBytes(), key);
    if (data == null) return false;

    return true;
  }

  public void useOutput(TransactionOutput out, ChainHash tx_id, int out_idx)
  {
    ByteString key = getKey(
      new AddressSpecHash(out.getRecipientSpecHash()),
      tx_id,
      out_idx);
    updates.put(key, null);

  }

  public void addOutputs(ImportedOutputList import_list)
    throws ValidationException
  {
    for(ImportedOutput io : import_list.getTxOutsList())
    {
      try
      {
        TransactionOutput out = TransactionOutput.parseFrom(io.getRawOutput());

        ByteString key = getKey(
          new AddressSpecHash(out.getRecipientSpecHash()),
          new ChainHash(io.getTxId()),
          io.getOutIdx());

        updates.put(key, io.getRawOutput());

      }
      catch(com.google.protobuf.InvalidProtocolBufferException e)
      {
        throw new ValidationException(e);
      }
    }

  }

  public void addOutput(List<ByteString> raw_output_list, TransactionOutput out, ChainHash tx_id, int out_idx)
    throws ValidationException
  {
    ByteString key = getKey(
      new AddressSpecHash(out.getRecipientSpecHash()),
      tx_id,
      out_idx);
    ByteString data = raw_output_list.get(out_idx);

    updates.put(key, data);
  }

  public static ByteString getKey(TransactionInput in)
  {
    return getKey(new AddressSpecHash(in.getSpecHash()),
          new ChainHash(in.getSrcTxId()), in.getSrcTxOutIdx());
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
