package snowblossom.shackleton;

import java.util.Locale;
import snowblossom.lib.AddressSpecHash;
import snowblossom.proto.TransactionOutput;
import snowblossom.shackleton.ChainIndex.NetStats;
import snowblossom.shackleton.MempoolIndex.Result;

/** The unconfirmed transactions waiting for the next block. */
public class MempoolPage
{
  public static String render(Shackleton sh)
  {
    Result r = sh.getMempoolIndex().getResult();
    NetStats ns = sh.getIndex().getNetStats();

    StringBuilder sb = new StringBuilder();
    sb.append("<div class='cards'>");
    sb.append(Html.card("Pending transactions", Html.num(r.reported_pool_size), "waiting in the node's mempool"));
    sb.append(Html.card("Listed below", Html.num(r.txs.size()),
      (r.template_shard >= 0) ? ("selected for the next block on shard " + r.template_shard) : "selected for the next block"));
    sb.append(Html.card("Total size", Html.bytes(r.total_size), "of the listed transactions"));
    sb.append(Html.card("Total fees", Html.snow(r.total_fee),
      (r.total_size > 0)
        ? String.format(Locale.US, "%.2f flakes per byte on average", (double) r.total_fee / (double) r.total_size)
        : "waiting for miners"));
    if (ns != null)
    {
      sb.append(Html.card("Expected in", Html.duration(ns.blocktime_avg_ms / 2), "half the average block time"));
    }
    sb.append("</div>");

    sb.append("<div class='panel' data-autorefresh='/fragment/mempool'>");
    sb.append(inner(sh));
    sb.append("</div>");

    sb.append("<div class='panel'><h2>About this view</h2>");
    sb.append("<p class='muted small'>The node's API lists the mempool only per address, so this page asks the node for a fresh block template ");
    sb.append("and shows the transactions it selected - the mempool's contents ordered best fee per byte first, exactly what the next mined block would confirm. ");
    sb.append("If the mempool ever holds more than one block's worth, the overflow is counted above but not listed. ");
    sb.append("On a network with several building shards a template covers one shard, so transactions queued for other shards are counted but not listed either. ");
    sb.append("The list refreshes every few seconds; each transaction links to its detail page, where it will show as confirmed once it is mined.</p>");
    sb.append("</div>");
    return sb.toString();
  }

  /** The refreshing part: heading, notices and the transaction table. */
  public static String inner(Shackleton sh)
  {
    Result r = sh.getMempoolIndex().getResult();
    StringBuilder sb = new StringBuilder();

    sb.append("<h2>Unconfirmed transactions <span class='muted small' style='font-weight:400'>updated ")
      .append(Html.time(r.generated)).append("</span></h2>");

    if (r.note != null)
    {
      sb.append("<div class='notice'>The mempool contents are not available right now: ")
        .append(Html.esc(r.note)).append(".</div>");
    }

    if (r.txs.size() == 0)
    {
      if (r.note == null)
      {
        sb.append("<p class='muted'>The mempool is empty - every known transaction is confirmed. ");
        sb.append("New transactions appear here in the seconds between being submitted and being mined into a block.</p>");
      }
      return sb.toString();
    }

    if (r.reported_pool_size > r.txs.size())
    {
      sb.append("<p class='muted small'>").append(Html.num(r.reported_pool_size - r.txs.size()))
        .append(" further pending transaction(s) are not in the next block's selection (other shards, dependencies or block size) and are not listed.</p>");
    }

    sb.append("<div class='tblwrap'><table class='tbl'><thead><tr>")
      .append("<th>Transaction</th><th class='num'>Value</th><th class='num'>Fee</th><th class='num'>Flakes/B</th>")
      .append("<th class='num'>Ins</th><th class='num'>Outs</th><th class='num'>Size</th><th>Recipients</th>")
      .append("</tr></thead><tbody>");

    for (TxView tv : r.txs)
    {
      sb.append("<tr>");
      sb.append("<td class='mono ell tight' title='").append(tv.hash.toString()).append("'>")
        .append("<a href='/tx/").append(tv.hash).append("'>").append(tv.hash).append("</a></td>");
      sb.append("<td class='num'>").append(Html.amountShort(tv.out_total)).append("</td>");
      sb.append("<td class='num'>").append(Html.amount(tv.fee)).append("</td>");
      sb.append("<td class='num'>").append(String.format(Locale.US, "%.2f", tv.feePerByte())).append("</td>");
      sb.append("<td class='num'>").append(tv.inputs.size()).append("</td>");
      sb.append("<td class='num'>").append(tv.inner.getOutputsCount()).append("</td>");
      sb.append("<td class='num'>").append(Html.bytes(tv.size)).append("</td>");

      sb.append("<td class='small'>");
      int shown = 0;
      for (TransactionOutput out : tv.inner.getOutputsList())
      {
        if (shown >= 2)
        {
          sb.append("<span class='muted'>+").append(tv.inner.getOutputsCount() - shown).append("</span>");
          break;
        }
        if (shown > 0) sb.append(" ");
        sb.append(Html.addrLink(new AddressSpecHash(out.getRecipientSpecHash()), sh.getParams()));
        shown++;
      }
      sb.append("</td>");
      sb.append("</tr>");
    }
    sb.append("</tbody></table></div>");
    return sb.toString();
  }
}
