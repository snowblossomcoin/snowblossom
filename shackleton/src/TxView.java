package snowblossom.shackleton;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import snowblossom.lib.*;
import snowblossom.proto.*;

/**
 * A transaction with parsed inner data and (where possible) resolved input values.
 * Input values are only carried in the transaction itself since SIP-4, older
 * transactions need a lookup of the source transaction output.
 */
public class TxView
{
  public final Transaction tx;
  public final TransactionInner inner;
  public final ChainHash hash;
  public final int size;
  public final boolean coinbase;
  public final long fee;
  public final ImmutableList<InputView> inputs;
  public final long in_total;       // -1 if any input value is unknown
  public final long out_total;
  public final TreeSet<String> signed_keys; // "claim:key" entries that carry a signature

  public static class InputView
  {
    public final TransactionInput in;
    public final AddressSpecHash address;
    public final ChainHash src_tx;
    public final int src_idx;
    public final long value; // -1 if unknown

    InputView(TransactionInput in, long value)
    {
      this.in = in;
      this.address = new AddressSpecHash(in.getSpecHash());
      this.src_tx = new ChainHash(in.getSrcTxId());
      this.src_idx = in.getSrcTxOutIdx();
      this.value = value;
    }
  }

  private TxView(Transaction tx, ChainCache cache, boolean resolve)
  {
    this.tx = tx;
    this.hash = new ChainHash(tx.getTxHash());
    this.inner = TransactionUtil.getInner(tx);
    this.size = tx.getSerializedSize();
    this.coinbase = inner.getIsCoinbase();
    this.fee = inner.getFee();

    signed_keys = new TreeSet<>();
    for (SignatureEntry se : tx.getSignaturesList())
    {
      signed_keys.add(se.getClaimIdx() + ":" + se.getKeyIdx());
    }

    ArrayList<InputView> lst = new ArrayList<>();
    long in_sum = 0;
    boolean unknown = false;
    for (TransactionInput in : inner.getInputsList())
    {
      long v = in.getValue();
      if ((v <= 0) && resolve && (cache != null))
      {
        v = resolveValue(cache, in);
      }
      if (v <= 0)
      {
        v = -1;
        unknown = true;
      }
      else
      {
        in_sum += v;
      }
      lst.add(new InputView(in, v));
    }
    inputs = ImmutableList.copyOf(lst);
    in_total = unknown ? -1 : in_sum;

    long out_sum = 0;
    for (TransactionOutput o : inner.getOutputsList())
    {
      out_sum += o.getValue();
    }
    out_total = out_sum;
  }

  private static long resolveValue(ChainCache cache, TransactionInput in)
  {
    try
    {
      Transaction src = cache.getTransaction(new ChainHash(in.getSrcTxId()));
      if (src == null) return -1;
      TransactionInner src_inner = TransactionUtil.getInner(src);
      int idx = in.getSrcTxOutIdx();
      if ((idx < 0) || (idx >= src_inner.getOutputsCount())) return -1;
      return src_inner.getOutputs(idx).getValue();
    }
    catch (Throwable t)
    {
      return -1;
    }
  }

  public static TxView of(Transaction tx, ChainCache cache, boolean resolve)
  {
    return new TxView(tx, cache, resolve);
  }

  public static TxView of(Transaction tx)
  {
    return new TxView(tx, null, false);
  }

  /** Net change this transaction causes for the given address (outputs received minus inputs spent) */
  public long netForAddress(AddressSpecHash addr)
  {
    long net = 0;
    for (TransactionOutput o : inner.getOutputsList())
    {
      if (addr.equals(o.getRecipientSpecHash())) net += o.getValue();
    }
    for (InputView iv : inputs)
    {
      if (iv.address.equals(addr))
      {
        if (iv.value > 0) net -= iv.value;
      }
    }
    return net;
  }

  public boolean hasUnknownInputForAddress(AddressSpecHash addr)
  {
    for (InputView iv : inputs)
    {
      if (iv.address.equals(addr) && (iv.value < 0)) return true;
    }
    return false;
  }

  public boolean involvesAddress(AddressSpecHash addr)
  {
    for (TransactionOutput o : inner.getOutputsList())
    {
      if (addr.equals(o.getRecipientSpecHash())) return true;
    }
    for (InputView iv : inputs)
    {
      if (iv.address.equals(addr)) return true;
    }
    return false;
  }

  public double feePerByte()
  {
    if (size == 0) return 0;
    return (double) fee / (double) size;
  }

  public List<AddressSpecHash> involvedAddresses()
  {
    ArrayList<AddressSpecHash> lst = new ArrayList<>();
    for (TransactionOutput o : inner.getOutputsList())
    {
      lst.add(new AddressSpecHash(o.getRecipientSpecHash()));
    }
    for (InputView iv : inputs)
    {
      lst.add(iv.address);
    }
    return lst;
  }

  public JSONObject toJson(NetworkParams params)
  {
    JSONObject o = new JSONObject();
    o.put("tx_hash", hash.toString());
    o.put("size", size);
    o.put("version", inner.getVersion());
    o.put("is_coinbase", coinbase);
    o.put("fee", fee);
    if (coinbase)
    {
      CoinbaseExtras ex = inner.getCoinbaseExtras();
      JSONObject cb = new JSONObject();
      cb.put("block_height", ex.getBlockHeight());
      cb.put("shard_id", ex.getShardId());
      cb.put("remark", HexUtil.getSafeString(ex.getRemarks()));
      JSONArray a = new JSONArray(); a.addAll(ex.getMotionsApprovedList()); cb.put("motions_approved", a);
      JSONArray r = new JSONArray(); r.addAll(ex.getMotionsRejectedList()); cb.put("motions_rejected", r);
      o.put("coinbase", cb);
    }
    JSONArray ins = new JSONArray();
    for (InputView iv : inputs)
    {
      JSONObject i = new JSONObject();
      i.put("address", iv.address.toAddressString(params));
      i.put("src_tx", iv.src_tx.toString());
      i.put("src_idx", iv.src_idx);
      if (iv.value >= 0) i.put("value", iv.value);
      ins.add(i);
    }
    o.put("inputs", ins);
    JSONArray outs = new JSONArray();
    int idx = 0;
    for (TransactionOutput out : inner.getOutputsList())
    {
      JSONObject j = new JSONObject();
      j.put("idx", idx);
      j.put("address", new AddressSpecHash(out.getRecipientSpecHash()).toAddressString(params));
      j.put("value", out.getValue());
      j.put("target_shard", out.getTargetShard());
      if (out.getForBenefitOfSpecHash().size() > 0)
      {
        j.put("for_benefit_of", new AddressSpecHash(out.getForBenefitOfSpecHash()).toAddressString(params));
      }
      if (out.hasRequirements())
      {
        if (out.getRequirements().getRequiredBlockHeight() > 0) j.put("required_block_height", out.getRequirements().getRequiredBlockHeight());
        if (out.getRequirements().getRequiredTime() > 0) j.put("required_time", out.getRequirements().getRequiredTime());
      }
      if (out.hasIds())
      {
        if (out.getIds().getUsername().size() > 0) j.put("username", HexUtil.getSafeString(out.getIds().getUsername()));
        if (out.getIds().getChannelname().size() > 0) j.put("channelname", HexUtil.getSafeString(out.getIds().getChannelname()));
      }
      outs.add(j);
      idx++;
    }
    o.put("outputs", outs);
    if (in_total >= 0) o.put("input_total", in_total);
    o.put("output_total", out_total);
    if (inner.getExtra().size() > 0) o.put("extra", HexUtil.getSafeString(inner.getExtra()));
    o.put("signatures", tx.getSignaturesCount());
    JSONArray claims = new JSONArray();
    for (AddressSpec spec : inner.getClaimsList())
    {
      JSONObject c = new JSONObject();
      c.put("address", AddressUtil.getAddressString(spec, params));
      c.put("required_signers", spec.getRequiredSigners());
      c.put("keys", spec.getSigSpecsCount());
      claims.add(c);
    }
    o.put("claims", claims);
    return o;
  }
}
