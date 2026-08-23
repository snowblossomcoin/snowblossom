package snowblossom.shackleton;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import snowblossom.lib.*;
import snowblossom.proto.*;

public class AddressPage
{
  public static final int PAGE_SIZE = 25;

  public static String render(Shackleton sh, AddressSpecHash address, int page)
    throws Exception
  {
    NetworkParams params = sh.getParams();
    ChainCache cache = sh.getCache();
    String addr_str = address.toAddressString(params);
    StringBuilder sb = new StringBuilder();

    // ---------------------------------------------------------- balances
    AddressBalance bal = AddressBalance.compute(sh, address);

    sb.append("<div class='panel'><h1>Address</h1>");
    sb.append("<div class='title-hash'>").append(Html.esc(addr_str)).append(Html.copy(addr_str)).append("</div>");
    sb.append("<p class='muted small' style='margin:6px 0 0'>spec hash <span class='mono'>").append(address.toString()).append("</span></p>");
    sb.append("</div>");

    sb.append("<div class='cards'>");
    sb.append(Html.card("Balance", Html.snow(bal.confirmed) + " <span class='muted small'>SNOW</span>", "confirmed"));
    String pend = (bal.pending == 0) ? "0" : ((bal.pending > 0 ? "+" : "") + Html.snow(bal.pending));
    sb.append(Html.card("Pending", pend + " <span class='muted small'>SNOW</span>", "unconfirmed change from mempool"));
    sb.append(Html.card("Spendable", Html.snow(bal.spendable) + " <span class='muted small'>SNOW</span>", "including unconfirmed"));
    sb.append(Html.card("UTXOs", Html.num(bal.utxos), bal.per_shard.size() > 1 ? "on " + bal.per_shard.size() + " shards" : "unspent outputs"));
    RichList.Result rl = sh.getRichList().getResult();
    if (rl != null)
    {
      int rank = rl.rankOf(addr_str);
      if (rank > 0)
      {
        sb.append(Html.cardLink("/richlist?page=" + ((rank - 1) / RichListPage.PAGE_SIZE + 1), "Rich list rank", "#" + Html.num(rank), Html.pct((double) bal.confirmed / rl.total) + " of supply"));
      }
    }
    sb.append("</div>");

    if (bal.error != null)
    {
      sb.append("<div class='notice'>Balance lookup failed: ").append(Html.esc(bal.error)).append("</div>");
    }
    else
    {
      sb.append("<p class='muted small' style='margin:-8px 0 14px'>Balance computed from the ").append(bal.describe()).append(".</p>");
    }

    if (bal.per_shard.size() > 1)
    {
      sb.append("<div class='panel'><h2>Balance per shard</h2><div class='tblwrap'><table class='tbl'><thead><tr><th>Shard</th><th class='num'>Confirmed</th><th class='num'>Pending</th><th class='num'>Spendable</th><th class='num'>UTXOs</th></tr></thead><tbody>");
      for (Map.Entry<Integer, long[]> me : bal.per_shard.entrySet())
      {
        long[] a = me.getValue();
        sb.append("<tr><td>").append(Html.shardBadge(me.getKey())).append("</td><td class='num'>").append(Html.amount(a[0]))
          .append("</td><td class='num'>").append(Html.amount(a[1])).append("</td><td class='num'>").append(Html.amount(a[2]))
          .append("</td><td class='num'>").append(a[3]).append("</td></tr>");
      }
      sb.append("</tbody></table></div></div>");
    }

    // ---------------------------------------------------------- mempool
    if (bal.mempool_txs.size() > 0)
    {
      sb.append("<div class='panel'><h2>Unconfirmed transactions ").append(Html.pill("warn", "" + bal.mempool_txs.size())).append("</h2>");
      for (TxView tv : bal.mempool_txs)
      {
        sb.append(TxPage.txCard(sh, tv, address, false));
      }
      sb.append("</div>");
    }

    // ---------------------------------------------------------- names / fbo
    try
    {
      TxOutList fbo = sh.getStub().getFBOList(RequestAddress.newBuilder().setAddressSpecHash(address.getBytes()).build());
      if (fbo.getOutListCount() > 0)
      {
        sb.append("<div class='panel'><h2>Identifiers held for this address</h2><div class='tblwrap'><table class='tbl'><thead><tr><th>Type</th><th>Name</th><th>Transaction</th><th class='num'>Value</th></tr></thead><tbody>");
        for (TxOutPoint op : fbo.getOutListList())
        {
          TransactionOutput o = op.getOut();
          String type = "output";
          String name = "";
          if (o.hasIds())
          {
            if (o.getIds().getUsername().size() > 0)
            {
              type = "username";
              name = HexUtil.getSafeString(o.getIds().getUsername());
            }
            else if (o.getIds().getChannelname().size() > 0)
            {
              type = "channel";
              name = HexUtil.getSafeString(o.getIds().getChannelname());
            }
          }
          sb.append("<tr><td>").append(type).append("</td><td><a href='/search?q=").append(Html.url(name)).append("'>").append(Html.esc(name)).append("</a></td><td>")
            .append(Html.txLink(new ChainHash(op.getTxHash()))).append(":").append(op.getOutIdx()).append("</td><td class='num'>").append(Html.amount(o.getValue())).append("</td></tr>");
        }
        sb.append("</tbody></table></div></div>");
      }
    }
    catch (Throwable t)
    {
      // FBO index may not be enabled, ignore
    }

    // ---------------------------------------------------------- history
    // Page 1 is built from the recent blocks held in memory by the chain index, which avoids
    // the node's address history query (slow for addresses with thousands of transactions).
    // Older pages (or addresses without recent activity) use the full history.
    List<HistoryRow> rows = null;
    boolean recent_mode = false;
    boolean has_next = false;
    int count = -1;
    boolean capped = false;
    int pages = 1;

    if (page == 1)
    {
      List<HistoryRow> recent = recentRows(sh, address, PAGE_SIZE + 1);
      if (recent != null)
      {
        recent_mode = true;
        has_next = recent.size() > PAGE_SIZE;
        rows = recent.subList(0, Math.min(PAGE_SIZE, recent.size()));
      }
    }

    if (!recent_mode)
    {
      HistoryList hl = null;
      try
      {
        hl = sh.getStub().getAddressHistory(RequestAddress.newBuilder().setAddressSpecHash(address.getBytes()).build());
      }
      catch (Throwable t)
      {
        sb.append("<div class='panel'><h2>Transactions</h2><div class='notice'>History lookup failed: ").append(Html.esc(t.toString())).append("</div></div>");
        return sb.toString();
      }
      if (hl.getNotEnabled())
      {
        sb.append("<div class='panel'><h2>Transactions</h2><div class='notice'>The node this explorer uses does not have the address index enabled, no transaction history available.</div></div>");
        return sb.toString();
      }

      // dedupe by tx hash
      LinkedHashMap<ChainHash, HistoryEntry> by_tx = new LinkedHashMap<>();
      for (HistoryEntry he : hl.getEntriesList())
      {
        ChainHash txh = new ChainHash(he.getTxHash());
        if (!by_tx.containsKey(txh)) by_tx.put(txh, he);
      }
      List<HistoryEntry> entries = new ArrayList<>(by_tx.values());
      Collections.sort(entries, new Comparator<HistoryEntry>()
      {
        public int compare(HistoryEntry a, HistoryEntry b)
        {
          if (a.getBlockHeight() != b.getBlockHeight()) return Integer.compare(b.getBlockHeight(), a.getBlockHeight());
          return Integer.compare(a.getShard(), b.getShard());
        }
      });
      count = entries.size();
      capped = hl.getEntriesCount() >= Globals.ADDRESS_HISTORY_MAX_REPLY;
      pages = Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
      page = WebUtil.clamp(page, 1, pages);
      has_next = page < pages;
      rows = new ArrayList<>();
      int start = (page - 1) * PAGE_SIZE;
      int end = Math.min(count, start + PAGE_SIZE);
      for (int i = start; i < end; i++)
      {
        HistoryEntry he = entries.get(i);
        ChainHash bh = new ChainHash(he.getBlockHash());
        BlockHeader header = cache.getHeader(bh);
        HistoryRow r = new HistoryRow();
        r.tx_hash = new ChainHash(he.getTxHash());
        r.block_hash = bh;
        r.height = he.getBlockHeight();
        r.shard = (header != null) ? header.getShardId() : he.getShard();
        r.timestamp = (header != null) ? header.getTimestamp() : 0;
        rows.add(r);
      }
    }

    sb.append("<div class='panel'><h2>Transactions");
    if (count >= 0) sb.append(" <span class='muted small'>").append(Html.num(count)).append(capped ? "+" : "").append("</span>");
    else sb.append(" <span class='muted small'>newest first</span>");
    sb.append("</h2>");
    if (capped)
    {
      sb.append("<div class='notice'>This address has more transactions than the node returns in one history query (").append(Html.num(Globals.ADDRESS_HISTORY_MAX_REPLY)).append("); the list is incomplete.</div>");
    }
    if (rows.size() == 0)
    {
      sb.append("<p class='muted'>No transactions found for this address.</p></div>");
      return sb.toString();
    }

    sb.append("<div class='tblwrap'><table class='tbl'><thead><tr><th>Time</th><th>Block</th><th>Transaction</th><th class='num'>Amount</th><th class='num'>Fee</th><th>Counterparties</th></tr></thead><tbody>");
    for (HistoryRow r : rows)
    {
      sb.append("<tr>");
      sb.append("<td>").append(r.timestamp > 0 ? Html.time(r.timestamp) : "<span class='muted'>?</span>").append("</td>");
      sb.append("<td>").append(Html.shardBadge(r.shard)).append(" <a href='/block/").append(r.block_hash).append("'>").append(Html.num(r.height)).append("</a></td>");
      sb.append("<td>").append(Html.txLink(r.tx_hash)).append("</td>");
      Transaction tx = (r.tx != null) ? r.tx : cache.getTransaction(r.tx_hash);
      if (tx == null)
      {
        sb.append("<td class='num muted'>?</td><td class='num'></td><td></td>");
      }
      else
      {
        TxView tv = TxView.of(tx, cache, true);
        long net = tv.netForAddress(address);
        boolean unknown = tv.hasUnknownInputForAddress(address);
        boolean sender = false;
        for (TxView.InputView iv : tv.inputs) if (iv.address.equals(address)) sender = true;
        String cls = (net > 0) ? "pos" : ((net < 0) ? "neg" : "");
        sb.append("<td class='num ").append(cls).append("'>").append(net > 0 ? "+" : "").append(Html.amount(net)).append(unknown ? " <span class='muted' title='some input values unknown'>?</span>" : "");
        if (tv.coinbase) sb.append(" ").append(Html.pill("accent", "mined"));
        sb.append("</td>");
        sb.append("<td class='num muted'>").append(sender ? Html.snow(tv.fee) : "").append("</td>");
        sb.append("<td class='small'>").append(counterparties(tv, address, params)).append("</td>");
      }
      sb.append("</tr>");
    }
    sb.append("</tbody></table></div>");
    if ((page > 1) || has_next) sb.append(Html.pager("/address/" + Html.esc(addr_str) + "?", page, has_next));
    sb.append("</div>");
    return sb.toString();
  }

  private static class HistoryRow
  {
    ChainHash tx_hash;
    ChainHash block_hash;
    int height;
    int shard;
    long timestamp;
    Transaction tx;
  }

  /**
   * Newest transactions of an address from the blocks in the chain index.
   * @return null if the index does not hold enough blocks to answer (caller falls back to the node's history)
   */
  private static List<HistoryRow> recentRows(Shackleton sh, AddressSpecHash address, int limit)
  {
    ChainIndex index = sh.getIndex();
    ChainCache cache = sh.getCache();
    ByteString addr_bytes = address.getBytes();
    ArrayList<HistoryRow> rows = new ArrayList<>();
    List<BlockInfo> recent = index.getRecentAll();
    if (recent.size() == 0) return null;
    for (BlockInfo bi : recent)
    {
      if (rows.size() >= limit) return rows;
      java.util.Set<ByteString> addrs = cache.addressesInBlock(bi.hash);
      if (addrs == null) return null;
      if (!addrs.contains(addr_bytes)) continue;
      Block blk = cache.getBlock(bi.hash);
      if (blk == null) return null;
      for (Transaction tx : blk.getTransactionsList())
      {
        TransactionInner inner;
        try
        {
          inner = TransactionUtil.getInner(tx);
        }
        catch (Throwable t)
        {
          continue;
        }
        boolean involved = false;
        for (TransactionInput in : inner.getInputsList()) if (addr_bytes.equals(in.getSpecHash())) { involved = true; break; }
        if (!involved) for (TransactionOutput out : inner.getOutputsList()) if (addr_bytes.equals(out.getRecipientSpecHash())) { involved = true; break; }
        if (!involved) continue;
        cache.cacheTransaction(tx);
        HistoryRow r = new HistoryRow();
        r.tx_hash = new ChainHash(tx.getTxHash());
        r.block_hash = bi.hash;
        r.height = bi.height;
        r.shard = bi.shard;
        r.timestamp = bi.timestamp;
        r.tx = tx;
        rows.add(r);
      }
    }
    // index exhausted before reaching the limit: older transactions may exist, use the full history
    return null;
  }

  private static String counterparties(TxView tv, AddressSpecHash self, NetworkParams params)
  {
    if (tv.coinbase) return "<span class='muted'>block reward</span>";
    LinkedHashMap<AddressSpecHash, Boolean> others = new LinkedHashMap<>();
    boolean sender = false;
    for (TxView.InputView iv : tv.inputs) if (iv.address.equals(self)) sender = true;
    if (sender)
    {
      for (TransactionOutput o : tv.inner.getOutputsList())
      {
        AddressSpecHash a = new AddressSpecHash(o.getRecipientSpecHash());
        if (!a.equals(self)) others.put(a, true);
      }
    }
    else
    {
      for (TxView.InputView iv : tv.inputs)
      {
        if (!iv.address.equals(self)) others.put(iv.address, true);
      }
    }
    if (others.size() == 0) return "<span class='muted'>self</span>";
    StringBuilder sb = new StringBuilder();
    int n = 0;
    for (AddressSpecHash a : others.keySet())
    {
      if (n > 0) sb.append(", ");
      sb.append(Html.addrLink(a, params));
      n++;
      if (n >= 3)
      {
        if (others.size() > 3) sb.append(" <span class='muted'>+").append(others.size() - 3).append(" more</span>");
        break;
      }
    }
    return sb.toString();
  }
}
