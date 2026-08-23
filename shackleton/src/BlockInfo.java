package snowblossom.shackleton;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import java.util.Map;
import java.util.TreeMap;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import snowblossom.lib.*;
import snowblossom.proto.*;

/**
 * Lightweight, immutable summary of a block derived from the full Block.
 * Cheap to keep around in caches and lists; the full Block is only needed
 * for the block detail page.
 */
public class BlockInfo
{
  public final ChainHash hash;
  public final ChainHash prev;
  public final int shard;
  public final int height;
  public final int version;
  public final int snow_field;
  public final long timestamp;
  public final int tx_count;
  public final int size;
  public final int tx_data_size;
  public final long fees;
  public final long subsidy;
  public final long coinbase_total;
  public final AddressSpecHash miner;
  public final String miner_addr;
  public final String remark;
  public final double target_diff;
  public final double block_diff;
  public final int import_count;
  public final int imported_output_count;
  public final ImmutableMap<Integer, Integer> imports;
  public final ImmutableList<Integer> exports;
  public final ImmutableList<Integer> motions_approved;
  public final ImmutableList<Integer> motions_rejected;
  public final BlockHeader header;

  private BlockInfo(Block blk, NetworkParams params)
  {
    header = blk.getHeader();
    hash = new ChainHash(header.getSnowHash());
    prev = new ChainHash(header.getPrevBlockHash());
    shard = header.getShardId();
    height = header.getBlockHeight();
    version = header.getVersion();
    snow_field = header.getSnowField();
    timestamp = header.getTimestamp();
    tx_count = blk.getTransactionsCount();
    size = blk.getSerializedSize();
    tx_data_size = header.getTxDataSizeSum();
    target_diff = PowUtil.getDiffForTarget(BlockchainUtil.targetBytesToBigInteger(header.getTarget()));
    block_diff = PowUtil.getDiffForTarget(BlockchainUtil.targetBytesToBigInteger(header.getSnowHash()));

    long fee_sum = 0;
    long cb_total = 0;
    AddressSpecHash miner_h = null;
    String remark_s = "";
    ImmutableList<Integer> m_app = ImmutableList.of();
    ImmutableList<Integer> m_rej = ImmutableList.of();

    for (int i = 0; i < blk.getTransactionsCount(); i++)
    {
      Transaction tx = blk.getTransactions(i);
      TransactionInner inner;
      try
      {
        inner = TransactionUtil.getInner(tx);
      }
      catch (Throwable t)
      {
        continue;
      }
      if (inner.getIsCoinbase())
      {
        CoinbaseExtras extras = inner.getCoinbaseExtras();
        remark_s = HexUtil.getSafeString(extras.getRemarks());
        m_app = ImmutableList.copyOf(extras.getMotionsApprovedList());
        m_rej = ImmutableList.copyOf(extras.getMotionsRejectedList());
        for (TransactionOutput out : inner.getOutputsList())
        {
          cb_total += out.getValue();
          if (miner_h == null)
          {
            miner_h = new AddressSpecHash(out.getRecipientSpecHash());
          }
        }
      }
      else
      {
        fee_sum += inner.getFee();
      }
    }
    fees = fee_sum;
    coinbase_total = cb_total;
    miner = miner_h;
    miner_addr = (miner_h == null) ? "" : AddressUtil.getAddressString(params.getAddressPrefix(), miner_h);
    remark = remark_s;
    motions_approved = m_app;
    motions_rejected = m_rej;

    long sub;
    if (header.getVersion() >= 2)
    {
      sub = ShardUtil.getBlockReward(params, header);
    }
    else
    {
      sub = PowUtil.getBlockReward(params, header.getBlockHeight());
    }
    subsidy = sub;

    TreeMap<Integer, Integer> imp = new TreeMap<>();
    int imp_count = 0;
    for (Map.Entry<Integer, BlockImportList> me : header.getShardImportMap().entrySet())
    {
      int n = me.getValue().getHeightMapCount();
      imp.put(me.getKey(), n);
      imp_count += n;
    }
    imports = ImmutableMap.copyOf(imp);
    import_count = imp_count;

    int imp_out = 0;
    for (ImportedBlock ib : blk.getImportedBlocksList())
    {
      for (ImportedOutputList iol : ib.getImportOutputsMap().values())
      {
        imp_out += iol.getTxOutsCount();
      }
    }
    imported_output_count = imp_out;

    TreeMap<Integer, ByteString> exp = new TreeMap<>();
    exp.putAll(header.getShardExportRootHashMap());
    exports = ImmutableList.copyOf(exp.keySet());
  }

  public static BlockInfo fromBlock(Block blk, NetworkParams params)
  {
    return new BlockInfo(blk, params);
  }

  /** Estimated hashes needed for a block at this target */
  public double getExpectedHashes()
  {
    return Math.pow(2.0, target_diff);
  }

  public int getGeneration()
  {
    return ShardUtil.getShardGeneration(shard);
  }

  public JSONObject toJson()
  {
    JSONObject o = new JSONObject();
    o.put("hash", hash.toString());
    o.put("prev_block_hash", prev.toString());
    o.put("shard", shard);
    o.put("height", height);
    o.put("version", version);
    o.put("snow_field", snow_field);
    o.put("timestamp", timestamp);
    o.put("tx_count", tx_count);
    o.put("size", size);
    o.put("tx_data_size", tx_data_size);
    o.put("fees", fees);
    o.put("subsidy", subsidy);
    o.put("coinbase_total", coinbase_total);
    o.put("miner", miner_addr);
    o.put("remark", remark);
    o.put("target_diff", target_diff);
    o.put("block_diff", block_diff);
    o.put("import_count", import_count);
    o.put("imported_outputs", imported_output_count);
    JSONObject imp = new JSONObject();
    for (Map.Entry<Integer, Integer> me : imports.entrySet())
    {
      imp.put("" + me.getKey(), me.getValue());
    }
    o.put("imports", imp);
    JSONArray exp = new JSONArray();
    exp.addAll(exports);
    o.put("exports", exp);
    if (motions_approved.size() > 0)
    {
      JSONArray a = new JSONArray();
      a.addAll(motions_approved);
      o.put("motions_approved", a);
    }
    if (motions_rejected.size() > 0)
    {
      JSONArray a = new JSONArray();
      a.addAll(motions_rejected);
      o.put("motions_rejected", a);
    }
    return o;
  }
}
