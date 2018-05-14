package snowblossom;

import snowblossom.proto.Transaction;
import snowblossom.proto.TransactionInner;
import snowblossom.proto.TransactionInput;
import snowblossom.proto.TransactionOutput;

import snowblossom.proto.SigSpec;
import snowblossom.proto.SignatureEntry;
import snowblossom.proto.AddressSpec;
import snowblossom.proto.WalletDatabase;
import snowblossom.proto.WalletKeyPair;

import java.security.MessageDigest;
import java.security.Signature;
import java.security.KeyPair;

import com.google.protobuf.ByteString;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.LinkedList;
import java.util.Collections;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashSet;


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
  protected static Transaction createTransaction(
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

  public static Transaction makeTransaction(WalletDatabase wallet,
    Collection<TransactionBridge> spendable,
    AddressSpecHash to,
    long value)
    throws ValidationException
  {
    TransactionOutput out = TransactionOutput.newBuilder()
      .setValue(value)
      .setRecipientSpecHash(to.getBytes())
      .build();

    return makeTransaction(wallet, spendable, ImmutableList.of(out));

  }
  
  public static Transaction makeTransaction(WalletDatabase wallet, 
    Collection<TransactionBridge> spendable, 
    List<TransactionOutput> output_list)
    throws ValidationException
  {
    TransactionInner.Builder tx_inner = TransactionInner.newBuilder();

    LinkedList<TransactionOutput> tx_outputs = new LinkedList<>();
    tx_outputs.addAll(output_list);


    long needed_input = 0;
    
    for(TransactionOutput tx_out : output_list)
    {
      needed_input += tx_out.getValue();
    }
    
    LinkedList<TransactionBridge> spendable_shuffle = new LinkedList<>();
    spendable_shuffle.addAll(spendable);

    Collections.shuffle(spendable_shuffle);

    HashSet<AddressSpecHash> needed_claims=new HashSet<>();

    LinkedList<TransactionInput> input_list = new LinkedList<>();

    while((needed_input > 0) && (spendable_shuffle.size() > 0))
    {
      TransactionBridge br = spendable_shuffle.pop();
      needed_input -= br.value;

      input_list.add(br.in);
      needed_claims.add( new AddressSpecHash(br.out.getRecipientSpecHash()) );
    }

    Collections.shuffle(input_list);
    tx_inner.addAllInputs(input_list);

    if (needed_input > 0) return null;

    if (needed_input < 0)
    {
      AddressSpecHash change = getRandomChangeAddress(wallet);
      TransactionOutput o = TransactionOutput.newBuilder()
        .setValue(-needed_input)
        .setRecipientSpecHash(change.getBytes())
        .build();

      tx_outputs.add(o);
    }

    Collections.shuffle(tx_outputs);

    tx_inner.addAllOutputs(tx_outputs);

    ArrayList<AddressSpec> claims = new ArrayList<>();

    for(AddressSpec spec : wallet.getAddressesList())
    {
      AddressSpecHash hash = AddressUtil.getHashForSpec(spec);
      if (needed_claims.contains(hash))
      {
        claims.add(spec);
      }
    }
    Collections.shuffle(claims);
    tx_inner.addAllClaims(claims);
    tx_inner.setVersion(1);

    ByteString tx_inner_data = tx_inner.build().toByteString();

    Transaction.Builder tx = Transaction.newBuilder();
    tx.setInnerData(tx_inner_data);
    ChainHash tx_hash = new ChainHash(DigestUtil.getMD().digest(tx_inner_data.toByteArray()));

    tx.setTxHash(tx_hash.getBytes());

    // Note number of needed signatures for each claim
    int[] needed_sigs = new int[claims.size()];
    for(int i =0;i<claims.size(); i++)
    {
      AddressSpec spec = claims.get(i);
      needed_sigs[i] = spec.getRequiredSigners();
    }
    //Sign

    for(WalletKeyPair key_pair : wallet.getKeysList())
    {
      ByteString public_key = key_pair.getPublicKey();

      for(int i=0; i<claims.size(); i++)
      {
        if (needed_sigs[i] > 0)
        {
          AddressSpec spec = claims.get(i);
          for(int j=0; j<spec.getSigSpecsCount(); j++)
          {
            SigSpec sig_spec = spec.getSigSpecs(j);
            if (sig_spec.getSignatureType() == key_pair.getSignatureType())
            if (sig_spec.getPublicKey().equals(public_key))
            {
              tx.addSignatures( SignatureEntry.newBuilder()
                .setClaimIdx(i)
                .setKeyIdx(j)
                .setSignature( SignatureUtil.sign(key_pair, tx_hash) )
                .build());
              needed_sigs[i]--;
            }

          }
        }
      }
    }


    return tx.build();
    
  }

  public static AddressSpecHash getRandomChangeAddress(WalletDatabase wallet)
  {
    LinkedList<AddressSpec> lst = new LinkedList<>();
    lst.addAll(wallet.getAddressesList());
    Collections.shuffle(lst);

    return AddressUtil.getHashForSpec(lst.pop());
  }

}
