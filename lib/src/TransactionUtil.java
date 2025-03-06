package snowblossom.lib;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.text.DecimalFormat;
import java.util.*;
import snowblossom.proto.*;

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

  public static ArrayList<ByteString> extractWireFormatTxOut(Transaction tx)
    throws ValidationException
  {
    try
    {
      CodedInputStream code_in = CodedInputStream.newInstance(tx.getInnerData().toByteArray());

      ArrayList<ByteString> lst = new ArrayList<>();

      while(true)
      {
        int tag = code_in.readTag();
        if (tag == 0) break;

        // The least signficiate 3 bits are the proto field type
        // so shift to get out field number, which is 5 for TranasctionOutput
        if (tag >> 3 == 5)
        {
          ByteArrayOutputStream b_out = new ByteArrayOutputStream();
          CodedOutputStream c_out = CodedOutputStream.newInstance(b_out);
          code_in.skipField(tag, c_out);
          c_out.flush();

          ByteString bs = ByteString.copyFrom(b_out.toByteArray());

          // So funny story...when you get an inner message like this as opposed to just serializing
          // the object, protobuf puts a tag and size on the coded input stream.  So we need to figure
          // out how many bytes that is an trim it off.
          CodedInputStream read_again = CodedInputStream.newInstance(bs.toByteArray());
          // Expected tag
          int tag2 = read_again.readTag();
          // Size of element
          int size = read_again.readInt32();

          // All we really care is how many bytes those two take.  For shorter messages
          // it will be 2, but could be higher if protobuf needs more bytes to encode the size
          int offset = read_again.getTotalBytesRead();

          bs = bs.substring(offset);
          lst.add(bs);
        }
        else
        {
          code_in.skipField(tag);
        }

      }
      return lst;
    }
    catch(java.io.IOException e)
    {
      throw new ValidationException(e);
    }
  }

  /**
   * Creates and signs super simple transaction.
   * Assumes the inputs given can all be spent with a single
   * claim using the given key pair.
   * Also assumes the inputs and output funds are exactly matched
   * with no fee.
   */
  @Deprecated
  public static Transaction createTransaction(
    List<TransactionInput> sources,
    List<TransactionOutput> dests,
    KeyPair key_pair)
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("TransactionUtil.createTransaction"))
    {
      MessageDigest md_bc = DigestUtil.getMD();
      Transaction.Builder tx = Transaction.newBuilder();

      TransactionInner.Builder inner = TransactionInner.newBuilder();
      inner.setVersion(1);

      inner.addAllOutputs(dests);
      inner.addAllInputs(sources);

      AddressSpec claim = AddressUtil.getSimpleSpecForKey(key_pair.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);

      inner.addClaims(claim);

      ByteString inner_data= inner.build().toByteString();
      tx.setInnerData(inner_data);
      tx.setTxHash(ByteString.copyFrom(md_bc.digest(inner_data.toByteArray())));


      Signature sig_engine = Signature.getInstance("ECDSA", Globals.getCryptoProviderName());
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

  @Deprecated
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

    return makeTransaction(wallet, spendable, ImmutableList.of(out), fee, null);

  }

  @Deprecated
  public static Transaction makeTransaction(WalletDatabase wallet,
    Collection<TransactionBridge> spendable,
    AddressSpecHash to,
    long value, long fee, AddressSpecHash change_address)
    throws ValidationException
  {
    TransactionOutput out = TransactionOutput.newBuilder()
      .setValue(value)
      .setRecipientSpecHash(to.getBytes())
      .build();

    return makeTransaction(wallet, spendable, ImmutableList.of(out), fee, change_address);

  }

  @Deprecated
  public static Transaction makeTransaction(
    WalletDatabase wallet,
    Collection<TransactionBridge> spendable,
    List<TransactionOutput> output_list,
    long fee,
    AddressSpecHash change_address)
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
      AddressSpecHash change = change_address;
      if (change == null)
      {
        change = getRandomChangeAddress(wallet);
      }
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
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("TransactionUtil.getRandomChangeAddress"))
    {
      LinkedList<AddressSpec> lst = new LinkedList<>();
      lst.addAll(wallet.getAddressesList());
      Collections.shuffle(lst);

      return AddressUtil.getHashForSpec(lst.pop());
    }
  }

  public static String prettyDisplayTx(Transaction tx, NetworkParams params)
    throws ValidationException
  {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PrintStream pout = new PrintStream(bout);

    prettyDisplayTx(tx, pout, params);
    pout.close();

    return new String(bout.toByteArray());

  }
  public static void prettyDisplayTx(Transaction tx, PrintStream out, NetworkParams params)
    throws ValidationException
  {
    ChainHash tx_hash = new ChainHash(tx.getTxHash());
    out.println("Transaction: " + tx_hash + " size: " + tx.toByteString().size());
    TreeSet<String> sign_set=new TreeSet<>();
    DecimalFormat df = new DecimalFormat("0.000000");
    DecimalFormat df_short = new DecimalFormat("0.00");

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

      if (o.getTargetShard() == 0)
      {
        out.println(String.format("  Output: %s %s", address, df.format(value)));
      }
      else
      {
        out.println(String.format("  Output: %s s:%d %s", address, o.getTargetShard(), df.format(value)));
      }
      if (o.getRequirements().getRequiredBlockHeight() > 0) out.println("    Required block height: " + o.getRequirements().getRequiredBlockHeight());
      if (o.getRequirements().getRequiredTime() > 0) out.println("    Required time: " + o.getRequirements().getRequiredTime());
      if (o.getForBenefitOfSpecHash().size() > 0)
      {
        String for_addr = new AddressSpecHash( o.getForBenefitOfSpecHash() ).toAddressString(params);
        out.println("    For benefit of: " + for_addr);
      }
      if (o.getIds() != null)
      {
        if (o.getIds().getUsername().size() > 0)
        {
          out.println("    Username: " + HexUtil.getSafeString(o.getIds().getUsername()));
        }
        if (o.getIds().getChannelname().size() > 0)
        {
          out.println("    Channelname: " + HexUtil.getSafeString(o.getIds().getChannelname()));
        }
      }
    }

    for(int c_idx = 0; c_idx < inner.getClaimsCount(); c_idx++)
    {
      AddressSpec claim = inner.getClaims(c_idx);

      out.print("  Claim: ");
      AddressUtil.prettyDisplayAddressSpec(claim, out, params, c_idx, sign_set);

    }

    double fee_flakes = inner.getFee();
    double fee_flakes_per_byte = fee_flakes / tx.toByteString().size();

    out.println(String.format("  Fee: %s (%s flakes per byte)",
      df.format( inner.getFee() / Globals.SNOW_VALUE_D),
      df_short.format( fee_flakes_per_byte )
      ));
    if (inner.getExtra().size() > 0)
    {
      out.println("  Extra: " + HexUtil.getSafeString(inner.getExtra()));
    }


  }

  /* Outputs transaction with HTML
   * inValues - The snow amounts for the transaction inputs in question so they can be displayed */
  public static void prettyDisplayTxHTML(Transaction tx, PrintStream out, NetworkParams params)
    throws ValidationException
  {
    out.println("<table class='table' style='max-width: 1400px;'><tr><th colspan=2>");

    ChainHash tx_hash = new ChainHash(tx.getTxHash());
    out.println("Transaction: " + tx_hash + " size: " + tx.toByteString().size());
    TreeSet<String> sign_set=new TreeSet<>();
    DecimalFormat df = new DecimalFormat("0.000000");
    DecimalFormat df_short = new DecimalFormat("0.00");

    for(SignatureEntry se : tx.getSignaturesList())
    {
      String key = "" + se.getClaimIdx() + ":" + se.getKeyIdx();
      sign_set.add(key);
    }

    TransactionInner inner = getInner(tx);

    out.println("</th></tr><tr><th>Inputs</th><th>Outputs</th></tr><tr><td style='width: 50%;'>");

    if (inner.getIsCoinbase())
    {
      CoinbaseExtras extras = inner.getCoinbaseExtras();
      out.print("  Coinbase - height: ");
      out.print(extras.getBlockHeight());
      out.print("<br /> remark: <b>");
      out.print(HexUtil.getSafeString(extras.getRemarks()));
      out.println("</b>");
      if (extras.getMotionsApprovedCount() > 0)
      {
        out.print("<br />    Motions Approved:" );
        for(int i : extras.getMotionsApprovedList())
        {
          out.print(' ');
          out.print(i);
        }
        out.println("<br />");
      }
      if (extras.getMotionsRejectedCount() > 0)
      {
        out.print("<br />    Motions Rejected:" );
        for(int i : extras.getMotionsRejectedList())
        {
          out.print(' ');
          out.print(i);
        }
        out.println("<br />");
      }
    }

    for(TransactionInput in : inner.getInputsList())
    {
      String address = AddressUtil.getAddressString(params.getAddressPrefix(), new AddressSpecHash( in.getSpecHash()));
      ChainHash src_tx = new ChainHash(in.getSrcTxId());
      int idx = in.getSrcTxOutIdx();
      if (in.getValue() > 0)
      {
        double amount = in.getValue() / Globals.SNOW_VALUE_D;
        out.println(String.format("<a href='/?search=%s'>%s</a> <b>%s</b><br />", address, address, df.format(amount)));
      }
      else
      {
        out.println(String.format("<a href='/?search=%s'>%s</a><br />", address, address));

      }
      //inValues.removeFirst();
    }
    for(int c_idx = 0; c_idx < inner.getClaimsCount(); c_idx++)
    {
      AddressSpec claim = inner.getClaims(c_idx);

      out.print("  Claim: ");
      AddressUtil.prettyDisplayAddressSpecShort(claim, out, params, c_idx, sign_set);

    }

    out.println("</td><td style='width: 50%;'>");
    for(TransactionOutput o : inner.getOutputsList())
    {
      String address =  AddressUtil.getAddressString(params.getAddressPrefix(), new AddressSpecHash( o.getRecipientSpecHash()));
      double value = o.getValue() / Globals.SNOW_VALUE_D;
      int dest_shard = o.getTargetShard();

      out.println(String.format("<a href='/?search=%s'>%s</a> s:%d <b>%s</b><br />",
        address, address, dest_shard, df.format(value)));
      //TODO - add requirements data
      if (o.getIds().getUsername().size() > 0)
      {
        String name =  HexUtil.getSafeString(o.getIds().getUsername());
        out.println(String.format("<li>Username: <a href='/?search=%s'>%s</a></li><br />", name, name));
      }
      if (o.getIds().getChannelname().size() > 0)
      {
        String name =  HexUtil.getSafeString(o.getIds().getChannelname());
        out.println(String.format("<li>Channel: <a href='/?search=%s'>%s</a></li><br />", name, name));
      }


    }

    out.println("</td></tr><td colspan=2>");

    double fee_flakes = inner.getFee();
    double fee_flakes_per_byte = fee_flakes / tx.toByteString().size();

    out.println(String.format("  Fee: %s (%s flakes per byte)",
      df.format( inner.getFee() / Globals.SNOW_VALUE_D),
      df_short.format( fee_flakes_per_byte )
      ));
    if (inner.getExtra().size() > 0)
    {
      out.println("  Extra: " + HexUtil.getSafeString(inner.getExtra()));
    }
    out.println("</td></table>");
  }
}
