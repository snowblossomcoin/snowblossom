package snowblossom;

import snowblossom.proto.Transaction;
import snowblossom.proto.TransactionInner;
import snowblossom.proto.TransactionInput;
import snowblossom.proto.TransactionOutput;

import snowblossom.proto.SigSpec;
import snowblossom.proto.SignatureEntry;
import snowblossom.proto.AddressSpec;

import java.security.MessageDigest;
import java.security.Signature;
import java.security.KeyPair;

import com.google.protobuf.ByteString;

import java.util.List;


public class TransactionUtil
{

  /**
   * Turns any parse problems into runtime exceptions
   * so only do if you know the transaction is already valid
   */
  public static TransactionInner getInner(Transaction tx)
  {
    try
    {
      return TransactionInner.parseFrom(tx.getInnerData());
    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates and signs super simple transaction.
   * Assumes the inputs given can all be spent with a single
   * claim using the given key pair.
   * Also assumes the inputs and output funds are exactly matched
   * with no fee.
   */
  public static Transaction createTransaction(
    List<TransactionInput> sources,
    List<TransactionOutput> dests,
    KeyPair key_pair)
  {
    try
    {
      MessageDigest md_bc = DigestUtil.getMD();
      Transaction.Builder tx = Transaction.newBuilder();

      TransactionInner.Builder inner = TransactionInner.newBuilder();
      inner.setVersion(1);

      inner.addAllOutputs(dests);
      inner.addAllInputs(sources);

      AddressSpec claim = AddressUtil.getSimpleSpecForKey(key_pair.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);
      
      AddressSpecHash addr_spec = AddressUtil.getHashForSpec(claim, DigestUtil.getMDAddressSpec());


      inner.addClaims(claim);

      ByteString inner_data= inner.build().toByteString();
      tx.setInnerData(inner_data);
      tx.setTxHash(ByteString.copyFrom(md_bc.digest(inner_data.toByteArray())));


      Signature sig_engine = Signature.getInstance("ECDSA");
      sig_engine.initSign(key_pair.getPrivate());
      sig_engine.update(tx.getTxHash().toByteArray());

      byte[] sig = sig_engine.sign();

      tx.addSignatures( SignatureEntry.newBuilder()
        .setClaimIdx(0)
        .setKeyIdx(0)
        .setSignature( ByteString.copyFrom(sig) )
        .build());


      Validation.checkTransactionBasics(tx.build(), false);

      return tx.build();
    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }

  }

}
