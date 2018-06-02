package snowblossomlib;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import snowblossom.proto.*;

import java.io.PrintStream;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.text.DecimalFormat;
import java.util.*;



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

  public static Transaction makeTransaction(WalletDatabase wallet,
    Collection<TransactionBridge> spendable,
    AddressSpecHash to,
    long value, long fee)
    throws ValidationException
  {
    TransactionOutput out = TransactionOutput.newBuilder()
      .setValue(value)
      .setRecipientSpecHash(to.getBytes())
      .build();

    return makeTransaction(wallet, spendable, ImmutableList.of(out), fee);

  }
  
  public static Transaction makeTransaction(WalletDatabase wallet, 
    Collection<TransactionBridge> spendable, 
    List<TransactionOutput> output_list, long fee)
    throws ValidationException
  {
    TransactionInner.Builder tx_inner = TransactionInner.newBuilder();

    tx_inner.setFee(fee);

    LinkedList<TransactionOutput> tx_outputs = new LinkedList<>();
    tx_outputs.addAll(output_list);


    long needed_input = fee;
    
    for(TransactionOutput tx_out : output_list)
    {
      needed_input += tx_out.getValue();
    }

    TreeMap<Double, TransactionBridge> spendable_map = new TreeMap<>();
    Random rnd = new Random();
    for(TransactionBridge br : spendable)
    {
      if (!br.spent)
      {
        double v=0.0;
        if (br.unconfirmed) v=rnd.nextDouble();
        else v=-rnd.nextDouble();

        // Put confirmed first, so they are picked before
        // anything uncomfirmed
        spendable_map.put(v, br);
      }
    }

    HashSet<AddressSpecHash> needed_claims=new HashSet<>();

    LinkedList<TransactionInput> input_list = new LinkedList<>();

    while((needed_input > 0) && (spendable_map.size() > 0))
    {
      TransactionBridge br = spendable_map.pollFirstEntry().getValue();
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

  public static void prettyDisplayTx(Transaction tx, PrintStream out, NetworkParams params)
    throws ValidationException
  {
    ChainHash tx_hash = new ChainHash(tx.getTxHash());
    out.println("Transaction: " + tx_hash + " size: " + tx.toByteString().size());
    TreeSet<String> sign_set=new TreeSet<>();
    DecimalFormat df = new DecimalFormat("0.000000");

    for(SignatureEntry se : tx.getSignaturesList())
    {
      String key = "" + se.getClaimIdx() + ":" + se.getKeyIdx();
      sign_set.add(key);
    }

    TransactionInner inner = getInner(tx);

    if (inner.getIsCoinbase())
    {
      CoinbaseExtras extras = inner.getCoinbaseExtras();
      out.print("  Coinbase - height:");
      out.print(extras.getBlockHeight());
      out.print(" remark:");
      out.print(HexUtil.getSafeString(extras.getRemarks()));
      out.println();
      if (extras.getMotionsApprovedCount() > 0)
      {
        out.print("    Motions Approved:" );
        for(int i : extras.getMotionsApprovedList())
        {
          out.print(' ');
          out.print(i);
        }
        out.println();
      }
      if (extras.getMotionsRejectedCount() > 0)
      {
        out.print("    Motions Rejected:" );
        for(int i : extras.getMotionsRejectedList())
        {
          out.print(' ');
          out.print(i);
        }
        out.println();
      }
    }

    for(TransactionInput in : inner.getInputsList())
    {
      String address = AddressUtil.getAddressString(params.getAddressPrefix(), new AddressSpecHash( in.getSpecHash()));
      ChainHash src_tx = new ChainHash(in.getSrcTxId());
      int idx = in.getSrcTxOutIdx();
      out.println(String.format("  Input: %s - %s:%d", address, src_tx, idx));
    }
    for(TransactionOutput o : inner.getOutputsList())
    {
      String address =  AddressUtil.getAddressString(params.getAddressPrefix(), new AddressSpecHash( o.getRecipientSpecHash()));
      double value = o.getValue() / Globals.SNOW_VALUE_D;

      out.println(String.format("  Output: %s %s", address, df.format(value)));
    }

    for(int c_idx = 0; c_idx < inner.getClaimsCount(); c_idx++)
    {
      AddressSpec claim = inner.getClaims(c_idx);

      out.print("  Claim: ");
      AddressUtil.prettyDisplayAddressSpec(claim, out, params, c_idx, sign_set);

    }
    out.println(String.format("  Fee: %s", df.format( inner.getFee() / Globals.SNOW_VALUE_D)));
    if (inner.getExtra().size() > 0)
    {
      out.println("  Extra: " + HexUtil.getSafeString(inner.getExtra()));
    }
    
    
  }

}
