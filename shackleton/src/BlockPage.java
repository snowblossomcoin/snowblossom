package snowblossom.shackleton;

import com.google.protobuf.ByteString;
import java.util.Map;
import java.util.TreeMap;
import snowblossom.lib.*;
import snowblossom.proto.*;

public class BlockPage
{
  public static final int TX_PAGE_SIZE = 50;

  public static String render(Shackleton sh, Block blk, int page)
    throws Exception
  {
    ChainCache cache = sh.getCache();
    ChainIndex index = sh.getIndex();
    NetworkParams params = sh.getParams();
    BlockHeader header = blk.getHeader();
    BlockInfo bi = cache.getBlockInfo(blk);
    int shard = bi.shard;
    int height = bi.height;
    boolean multi = index.getShardStats().size() > 1 || header.getShardImportMap().size() > 0 || shard != 0;

    StringBuilder sb = new StringBuilder();

    // confirmations / chain membership
    int head_height = index.getHeightForConfirmations(shard);
    if (head_height < 0) head_height = cache.getShardHeight(shard);
    BlockHeader canonical = cache.getHeaderAtHeight(shard, height);
    boolean in_chain = (canonical != null) && canonical.getSnowHash().equals(header.getSnowHash());
    int confirmations = (head_height >= height) ? (head_height - height + 1) : 0;

    // navigation
    String prev_link = null;
    String next_link = null;
    if (height > 0)
    {
      BlockHeader ph = cache.getHeader(bi.prev);
      if (ph != null)
      {
        prev_link = "<a class='btn' href='/block/" + bi.prev + "'>&larr; " + ((ph.getShardId() == shard) ? "" : "S" + ph.getShardId() + " ") + Html.num(ph.getBlockHeight()) + "</a>";
      }
    }
    StringBuilder next = new StringBuilder();
    BlockHeader nh = cache.getHeaderAtHeight(shard, height + 1);
    if ((nh != null) && nh.getPrevBlockHash().equals(header.getSnowHash()))
    {
      next.append("<a class='btn' href='/block/").append(new ChainHash(nh.getSnowHash())).append("'>").append(Html.num(height + 1)).append(" &rarr;</a>");
    }
    else
    {
      // maybe this block was the split point, look for children
      for (int c : ShardUtil.getShardChildIds(shard))
      {
        if (c > params.getMaxShardId()) break;
        BlockHeader ch = cache.getHeaderAtHeight(c, height + 1);
        if ((ch != null) && ch.getPrevBlockHash().equals(header.getSnowHash()))
        {
          next.append("<a class='btn' href='/block/").append(new ChainHash(ch.getSnowHash())).append("'>S").append(c).append(" ").append(Html.num(height + 1)).append(" &rarr;</a>");
        }
      }
    }
    if (next.length() > 0) next_link = next.toString();

    sb.append("<div class='panel'>");
    sb.append("<h1>Block ").append(Html.shardBadge(shard)).append(" #").append(Html.num(height));
    if (!in_chain) sb.append(" ").append(Html.pill("warn", "not in main chain"));
    else if (confirmations > 0) sb.append(" ").append(Html.pill("ok", Html.num(confirmations) + " confirmation" + (confirmations == 1 ? "" : "s")));
    sb.append("<span class='more'>");
    if (prev_link != null) sb.append(prev_link).append(" ");
    sb.append("<a class='btn' href='/blocks?shard=").append(shard).append("&from=").append(height).append("'>List</a> ");
    if (next_link != null) sb.append(next_link);
    sb.append("</span></h1>");
    sb.append("<div class='title-hash'>").append(bi.hash.toString()).append(Html.copy(bi.hash.toString())).append("</div>");

    sb.append("<div class='grid2' style='margin-top:14px'>");
    sb.append("<dl class='kv'>");
    sb.append(Html.kv("Timestamp", Html.timeFull(bi.timestamp)));
    sb.append(Html.kv("Transactions", Html.num(bi.tx_count) + " <span class='muted small'>(" + Html.bytes(bi.tx_data_size) + " tx data)</span>"));
    sb.append(Html.kv("Size", Html.bytes(bi.size) + " <span class='muted small'>(" + Html.num(bi.size) + " bytes)</span>"));
    sb.append(Html.kv("Block reward", Html.amount(bi.coinbase_total) + " SNOW"));
    sb.append(Html.kv("&nbsp;&nbsp;subsidy", Html.amount(bi.subsidy) + " SNOW"));
    sb.append(Html.kv("&nbsp;&nbsp;fees", Html.amount(bi.fees) + " SNOW"));
    if (bi.coinbase_total != bi.subsidy + bi.fees)
    {
      long diff = bi.coinbase_total - bi.subsidy - bi.fees;
      sb.append(Html.kv("&nbsp;&nbsp;difference", "<span class='" + (diff < 0 ? "neg" : "warn") + "'>" + (diff > 0 ? "+" : "") + Html.snow(diff) + " SNOW</span> <span class='muted small'>coinbase vs. expected subsidy + fees</span>"));
    }
    sb.append(Html.kv("Miner", Html.addrLinkFull(bi.miner_addr)));
    if (bi.remark.length() > 0) sb.append(Html.kv("Remark", "<b>" + Html.esc(bi.remark) + "</b>"));
    if (bi.motions_approved.size() > 0) sb.append(Html.kv("Motions approved", Html.esc(bi.motions_approved.toString())));
    if (bi.motions_rejected.size() > 0) sb.append(Html.kv("Motions rejected", Html.esc(bi.motions_rejected.toString())));
    sb.append("</dl>");

    sb.append("<dl class='kv'>");
    sb.append(Html.kv("Difficulty", Html.dec(bi.target_diff, 3) + " <span class='muted small'>target · block hash " + Html.dec(bi.block_diff, 3) + "</span>"));
    sb.append(Html.kv("Expected hashes", Html.dec(bi.getExpectedHashes() / 1e9, 2) + " G"));
    sb.append(Html.kv("Snow field", "#" + bi.snow_field + " " + Html.esc(fieldName(params, bi.snow_field))));
    sb.append(Html.kv("Version", "" + bi.version + (bi.version >= 2 ? " <span class='muted small'>(shard aware)</span>" : "")));
    sb.append(Html.kv("Previous block", height > 0 ? Html.blockLinkFull(bi.prev) : "<span class='muted'>genesis</span>"));
    sb.append(Html.kv("Merkle root", "<span class='mono hash'>" + HexUtil.getHexString(header.getMerkleRootHash()) + "</span>"));
    sb.append(Html.kv("UTXO root", "<span class='mono hash'>" + HexUtil.getHexString(header.getUtxoRootHash()) + "</span>"));
    sb.append(Html.kv("Nonce", "<span class='mono'>" + HexUtil.getHexString(header.getNonce()) + "</span>"));
    sb.append(Html.kv("Target", "<span class='mono hash'>" + HexUtil.getHexString(header.getTarget()).substring(0, 32) + "…</span>"));
    sb.append(Html.kv("PoW proofs", "" + header.getPowProofCount()));
    sb.append(Html.kv("Raw", "<a href='/api/block/" + bi.hash + "'>JSON</a>"));
    sb.append("</dl>");
    sb.append("</div></div>\n");

    // ---------------------------------------------------------- shard data
    if (multi)
    {
      sb.append("<div class='panel'><h2>Shard data</h2>");
      sb.append("<div class='grid2'>");
      sb.append("<div><dl class='kv'>");
      sb.append(Html.kv("Shard", Html.shardBadge(shard) + " generation " + bi.getGeneration() + (BlockchainUtil.isCoordinator(shard) ? " · coordinator" : "")));
      if (shard > 0) sb.append(Html.kv("Parent shard", Html.shardBadge(ShardUtil.getShardParentId(shard))));
      StringBuilder kids = new StringBuilder();
      for (int c : ShardUtil.getShardChildIds(shard))
      {
        if (c <= params.getMaxShardId()) kids.append(Html.shardBadge(c)).append(" ");
      }
      if (kids.length() > 0) sb.append(Html.kv("Child shards", kids.toString() + "<span class='muted small'>(if this shard splits)</span>"));
      sb.append(Html.kv("Shard reward share", "1/" + ShardUtil.getShardShareDivisor(shard) + " <span class='muted small'>of the per-height subsidy (direct + import rewards)</span>"));
      sb.append(Html.kv("Imported outputs", Html.num(bi.imported_output_count)));
      sb.append("</dl></div>");

      sb.append("<div>");
      if (header.getShardImportMap().size() > 0)
      {
        sb.append("<h3>Imported blocks</h3><div class='tblwrap'><table class='tbl'><thead><tr><th>Shard</th><th class='num'>Height</th><th>Block</th></tr></thead><tbody>");
        for (Map.Entry<Integer, BlockImportList> me : new TreeMap<>(header.getShardImportMap()).entrySet())
        {
          int imp_shard = me.getKey();
          for (Map.Entry<Integer, ByteString> he : new TreeMap<>(me.getValue().getHeightMapMap()).entrySet())
          {
            ChainHash imp_hash = new ChainHash(he.getValue());
            sb.append("<tr><td>").append(Html.shardBadge(imp_shard)).append("</td><td class='num'>").append(Html.blockHeightLink(imp_shard, he.getKey())).append("</td><td>").append(Html.blockLink(imp_hash)).append("</td></tr>");
          }
        }
        sb.append("</tbody></table></div>");
      }
      else
      {
        sb.append("<p class='muted small'>No blocks imported from other shards.</p>");
      }
      if (header.getShardExportRootHashMap().size() > 0)
      {
        sb.append("<h3 style='margin-top:12px'>Exported outputs</h3><div class='tblwrap'><table class='tbl'><thead><tr><th>To shard</th><th>Export root hash</th></tr></thead><tbody>");
        for (Map.Entry<Integer, ByteString> me : new TreeMap<>(header.getShardExportRootHashMap()).entrySet())
        {
          sb.append("<tr><td>").append(Html.shardBadge(me.getKey())).append("</td><td class='mono small'>").append(HexUtil.getHexString(me.getValue())).append("</td></tr>");
        }
        sb.append("</tbody></table></div>");
      }
      sb.append("</div></div>");

      if (blk.getImportedBlocksCount() > 0)
      {
        sb.append("<h3 style='margin-top:12px'>Imported block details</h3><div class='tblwrap'><table class='tbl'><thead><tr><th>Shard</th><th class='num'>Height</th><th>Block</th><th>Outputs imported</th></tr></thead><tbody>");
        for (ImportedBlock ib : blk.getImportedBlocksList())
        {
          BlockHeader ih = ib.getHeader();
          StringBuilder outs = new StringBuilder();
          for (Map.Entry<Integer, ImportedOutputList> me : new TreeMap<>(ib.getImportOutputsMap()).entrySet())
          {
            outs.append("from ").append(Html.shardBadge(me.getKey())).append(" ").append(me.getValue().getTxOutsCount()).append(" · ");
          }
          sb.append("<tr><td>").append(Html.shardBadge(ih.getShardId())).append("</td><td class='num'>").append(Html.num(ih.getBlockHeight())).append("</td><td>")
            .append(Html.blockLink(new ChainHash(ih.getSnowHash()))).append("</td><td class='wrap'>").append(outs.length() == 0 ? "<span class='muted'>none</span>" : outs.toString()).append("</td></tr>");
        }
        sb.append("</tbody></table></div>");
      }
      sb.append("</div>\n");
    }

    // ---------------------------------------------------------- transactions
    int tx_total = blk.getTransactionsCount();
    int pages = Math.max(1, (tx_total + TX_PAGE_SIZE - 1) / TX_PAGE_SIZE);
    page = WebUtil.clamp(page, 1, pages);
    int start = (page - 1) * TX_PAGE_SIZE;
    int end = Math.min(tx_total, start + TX_PAGE_SIZE);

    sb.append("<div class='panel'><h2>Transactions <span class='muted small'>").append(Html.num(tx_total));
    if (pages > 1) sb.append(" · showing ").append(start + 1).append("–").append(end);
    sb.append("</span></h2>");
    for (int i = start; i < end; i++)
    {
      Transaction tx = blk.getTransactions(i);
      cache.cacheTransaction(tx);
      TxView tv = TxView.of(tx, cache, true);
      sb.append(TxPage.txCard(sh, tv, null, false));
    }
    if (pages > 1)
    {
      sb.append(Html.pager("/block/" + bi.hash + "?", page, page < pages));
    }
    sb.append("</div>");

    return sb.toString();
  }

  public static String fieldName(NetworkParams params, int field)
  {
    SnowFieldInfo sf = params.getSnowFieldInfo(field);
    if (sf == null) return "field " + field;
    return sf.getName();
  }
}
