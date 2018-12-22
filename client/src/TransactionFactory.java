package snowblossom.client;


import snowblossom.util.proto.*;
import snowblossom.proto.*;
import snowblossom.lib.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.Random;
import java.util.HashMap;
import java.util.HashSet;
import java.text.DecimalFormat;

import com.google.protobuf.ByteString;
import com.google.common.collect.TreeMultimap;

public class TransactionFactory
{


  public static TransactionFactoryResult createTransaction(TransactionFactoryConfig config, WalletDatabase db, SnowBlossomClient client)
    throws Exception
  {
    TransactionInner.Builder inner = TransactionInner.newBuilder();
    inner.setVersion(1);

    ArrayList<TransactionOutput> outputs = new ArrayList<>();
    ArrayList<TransactionInput> inputs = new ArrayList<>();
    ArrayList<AddressSpec> claims = new ArrayList<>();

    HashMap<AddressSpecHash, AddressSpec> known_claims = new HashMap<>();
    HashSet<AddressSpecHash> included_claims = new HashSet<>();

    for(AddressSpec spec : db.getAddressesList())
    {
      AddressSpecHash hash = AddressUtil.getHashForSpec(spec);
      known_claims.put(hash, spec);
    }

    if (config.getExtra().size() > 0) inner.setExtra(config.getExtra());
    
    long needed_for_outputs = 0;
    for(TransactionOutput out : config.getOutputsList())
    {
      outputs.add(out);
      needed_for_outputs += out.getValue();
    }

    if (config.getSendAll())
    {
      if (config.getOutputsCount() != 1)
      {
        throw new ValidationException("For send_all, there must be exactly one output");
      }
      if (outputs.get(0).getValue() != 0L)
      {
        throw new ValidationException("For send_all, the one output must have 0 value initially");
      }
      
    }

    FeeEstimate fee_estimate = null;
    long fee = 0L;
    if (config.getFeeUseEstimate())
    {
      fee_estimate = client.getFeeEstimate();
      fee = (long)(estimateSize(inner.build(), 2, outputs.size() + 1, claims) * fee_estimate.getFeePerByte());
    }
    else
    {
      fee = config.getFeeFlat();
    }

    TreeMap<Double, UTXOEntry> usable_inputs = new TreeMap<>();
    Random rnd = new Random();
    if (config.getInputSpecificList())
    {
      for(UTXOEntry e : config.getInputsList())
      {
        usable_inputs.put(rnd.nextDouble(), e);
      }
    }
    else if (config.getInputConfirmedThenPending() || config.getInputConfirmedOnly())
    {
      boolean use_pending=false;
      if (config.getInputConfirmedThenPending()) use_pending=true;

      for(TransactionBridge br : client.getAllSpendable())
      {
        boolean confirmed = !br.unconfirmed;
        if (!br.spent)
        if (use_pending || confirmed)
        {
          UTXOEntry e = br.toUTXOEntry();
          double priority = rnd.nextDouble();
          if (confirmed) priority += 1e3;

          usable_inputs.put(-priority, e);
        }
      }
    } 
    else
    {
      throw new ValidationException("No input mode specified");
    }

    long input_total = 0;
    while((input_total < needed_for_outputs + fee) || (config.getSendAll() && (usable_inputs.size() > 0)))
    {
      if (usable_inputs.size() == 0)
      {
        long short_val = needed_for_outputs + fee - input_total;
        double short_d = (double) short_val / Globals.SNOW_VALUE_D;
        DecimalFormat df = new DecimalFormat("0.000000");
        throw new ValidationException(String.format("Insufficent funds.  Short: %s SNOW", df.format(short_d)));
      }
      UTXOEntry e = usable_inputs.pollFirstEntry().getValue();

      inputs.add(
        TransactionInput.newBuilder()
          .setSpecHash( e.getSpecHash() )
          .setSrcTxId( e.getSrcTx() )
          .setSrcTxOutIdx( e.getSrcTxOutIdx() )
          .build());

      AddressSpecHash spec_hash = new AddressSpecHash( e.getSpecHash() );
      if (!included_claims.contains(spec_hash))
      {
        AddressSpec spec = known_claims.get(spec_hash);
        if (spec == null) throw new ValidationException("Input with no known claim: " + spec_hash);

        claims.add(spec);
        included_claims.add(spec_hash);
      }

      input_total += e.getValue();


      if (fee_estimate!=null)
      {
        // As we include more inputs, the fee estimation will go up
        fee = (long)(estimateSize(inner.build(), inputs.size(), outputs.size() + 1, claims) * fee_estimate.getFeePerByte());
      }
    }
    if (config.getSendAll())
    {
      TransactionOutput init_out = outputs.get(0);
      long out_value = input_total - fee;
      TransactionOutput new_out = TransactionOutput.newBuilder().mergeFrom(init_out).setValue(out_value).build();
      outputs.clear();
      outputs.add(new_out);
      needed_for_outputs = out_value;
    }

    inner.setFee(fee);

    long change = input_total - fee - needed_for_outputs;

    int change_count = 0;
    if (change > 0) change_count++;
    if (config.getSplitChangeOver() > 0)
    {
      // Example, change is 7500.  Split change over is 5000.
      // So we have one additional change outout
      change_count += change / config.getSplitChangeOver();
    }

    for(int chg =0; chg < change_count; chg++)
    {
      long value = change;
      if (change_count > 1)
      {
        if (chg == 0) value = change % config.getSplitChangeOver();
        else value = config.getSplitChangeOver();
      }
      AddressSpecHash change_addr = null;
      if (config.getChangeRandomFromWallet())
      {
        change_addr = TransactionUtil.getRandomChangeAddress(db);
      }
      else if (config.getChangeFreshAddress())
      {
        change_addr = client.getPurse().getUnusedAddress(false, false);
      }
      else if (config.getChangeSpecificAddresses())
      {
        int count = config.getChangeAddressesCount();
        if (count ==0) throw new ValidationException("Change specific addresses specified but no list provided");
        int idx = rnd.nextInt(count);
        change_addr = new AddressSpecHash(config.getChangeAddresses(idx));
      }
      else
      {
        throw new ValidationException("Change mode not specified");
      }

      outputs.add(
        TransactionOutput.newBuilder()
          .setRecipientSpecHash( change_addr.getBytes() )
          .setValue(value)
          .build());

    }


    Collections.shuffle(outputs);
    Collections.shuffle(inputs);
    Collections.shuffle(claims);

    inner.addAllOutputs(outputs);
    inner.addAllInputs(inputs);
    inner.addAllClaims(claims);

    Transaction.Builder tx = Transaction.newBuilder();
    ByteString inner_data= inner.build().toByteString();
    tx.setInnerData(inner_data);
    tx.setTxHash(ByteString.copyFrom(DigestUtil.getMD().digest(inner_data.toByteArray())));

    if (config.getSign())
    {
      return signTransaction(tx.build(), db);
    }
    return TransactionFactoryResult.newBuilder()
      .setTx(tx.build())
      .setFee(fee)
      .setAllSigned(false)
      .build();

  }

  public static int estimateSize(TransactionInner inner, int inputs, int outputs, List<AddressSpec> claims)
    throws ValidationException
  {
    int size = 50; //general overhead
    size += Globals.BLOCKCHAIN_HASH_LEN;
    size += inner.toByteString().size();

    int per_input = Globals.ADDRESS_SPEC_HASH_LEN + Globals.BLOCKCHAIN_HASH_LEN + 4;
    int per_output = Globals.ADDRESS_SPEC_HASH_LEN + 8;

    size += inputs * per_input;
    size += outputs * per_output;

    for(AddressSpec claim : claims)
    {
      size += claim.toByteString().size();

      // For multisig, we are not considering that we might not need
      // all of these sigs
      for(SigSpec spec : claim.getSigSpecsList())
      {
        int algo = spec.getSignatureType();
        size += SignatureUtil.estimateSignatureBytes(algo);
      }

    }

    return size;
  }


  public static TransactionFactoryResult signTransaction(Transaction input, WalletDatabase db)
		throws ValidationException
  {
		TransactionInner inner = TransactionUtil.getInner(input);
		ArrayList<AddressSpec> claims = new ArrayList<>();
		claims.addAll(inner.getClaimsList());

    // Note number of needed signatures for each claim
    int[] needed_sigs = new int[claims.size()];
    for(int i =0;i<claims.size(); i++)
    {
      AddressSpec spec = claims.get(i);
      needed_sigs[i] = spec.getRequiredSigners();
    }

    TreeMultimap<Integer, Integer> existing_sigs = TreeMultimap.create();

		// Note any existing signatures
		for(SignatureEntry sig : input.getSignaturesList())
    {
      int claim_idx = sig.getClaimIdx();
      int key_idx = sig.getKeyIdx();
      existing_sigs.put(claim_idx, key_idx);
      needed_sigs[claim_idx]--;
    }

		Transaction.Builder tx = Transaction.newBuilder().mergeFrom(input);
		int added_sigs = 0;

		ChainHash tx_hash = new ChainHash(tx.getTxHash());

    for(WalletKeyPair key_pair : db.getKeysList())
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
						if (!existing_sigs.containsEntry(i,j))
            if (sig_spec.getSignatureType() == key_pair.getSignatureType())
            if (sig_spec.getPublicKey().equals(public_key))
            {
              tx.addSignatures( SignatureEntry.newBuilder()
                .setClaimIdx(i)
                .setKeyIdx(j)
                .setSignature( SignatureUtil.sign(key_pair, tx_hash) )
                .build());
              needed_sigs[i]--;
							added_sigs++;
            }

          }
        }
      }
    }

		boolean all_signed=true;

    for(int i =0;i<claims.size(); i++)
		{
			if (needed_sigs[i] > 0) all_signed=false;
		}
           
     return TransactionFactoryResult.newBuilder()
      .setTx(tx.build())
      .setFee(inner.getFee())
      .setAllSigned(all_signed)
			.setSignaturesAdded(added_sigs)
      .build();

  }


}
