package snowblossom.shackleton;

import java.util.Map;

public class RichListPage
{
  public static final int PAGE_SIZE = 100;

  public static String render(Shackleton sh, Map<String, String> q)
  {
    RichList.Result r = sh.getRichList().getResult();
    StringBuilder sb = new StringBuilder();
    if (r == null)
    {
      sb.append("<div class='panel'><h1>Rich list</h1><p class='muted'>The rich list is computed from the full UTXO set of every shard shortly after the explorer starts and is refreshed periodically. Please check back in a few minutes.</p></div>");
      return sb.toString();
    }
    int page = WebUtil.intParam(q, "page", 1);
    int pages = Math.max(1, (r.entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    page = WebUtil.clamp(page, 1, pages);
    boolean multi = r.shard_totals.size() > 1;

    sb.append("<div class='cards'>");
    sb.append(Html.card("Circulating supply", Html.dec(r.total / 1e6, 2) + " <span class='muted small'>SNOW</span>", "sum of all unspent outputs"));
    sb.append(Html.card("Addresses", Html.num(r.address_count), "with a non-zero balance"));
    sb.append(Html.card("UTXOs", Html.num(r.utxo_count), multi ? "on " + r.shard_totals.size() + " shards" : "unspent outputs"));
    sb.append(Html.card("Top 10 / 100 / 1000", Html.dec(100.0 * r.top10 / r.total, 1) + "% / " + Html.dec(100.0 * r.top100 / r.total, 1) + "% / " + Html.dec(100.0 * r.top1000 / r.total, 1) + "%", "share of supply"));
    sb.append(Html.card("Computed", Html.time(r.generated), "at height " + Html.num(r.max_height) + " in " + Html.duration(r.elapsed_ms)));
    sb.append("</div>");

    if (multi)
    {
      sb.append("<div class='panel'><h2>Supply per shard</h2><div class='tblwrap'><table class='tbl'><thead><tr><th>Shard</th><th class='num'>Height</th><th class='num'>Coins</th><th class='num'>Share</th><th class='num'>UTXOs</th></tr></thead><tbody>");
      for (Map.Entry<Integer, Long> me : r.shard_totals.entrySet())
      {
        int s = me.getKey();
        sb.append("<tr><td>").append(Html.shardBadge(s)).append("</td><td class='num'>").append(Html.num(r.shard_heights.getOrDefault(s, 0))).append("</td><td class='num'>")
          .append(Html.amount(me.getValue())).append("</td><td class='num'>").append(Html.pct((double) me.getValue() / r.total)).append("</td><td class='num'>")
          .append(Html.num(r.shard_utxos.getOrDefault(s, 0L))).append("</td></tr>");
      }
      sb.append("</tbody></table></div></div>");
    }

    sb.append("<div class='panel'><h1>Rich list</h1>");
    sb.append("<div class='tblwrap'><table class='tbl'><thead><tr><th class='num'>#</th><th>Address</th><th class='num'>Balance</th><th class='num'>Share</th><th class='num'>UTXOs</th>");
    if (multi) sb.append("<th>Shards</th>");
    sb.append("</tr></thead><tbody>");
    int start = (page - 1) * PAGE_SIZE;
    int end = Math.min(r.entries.size(), start + PAGE_SIZE);
    for (int i = start; i < end; i++)
    {
      RichList.Entry e = r.entries.get(i);
      sb.append("<tr><td class='num muted'>").append(i + 1).append("</td><td>").append(Html.addrLinkFull(e.address)).append("</td><td class='num'>")
        .append(Html.amount(e.balance)).append("</td><td class='num'>").append(Html.pct((double) e.balance / r.total)).append("</td><td class='num'>").append(Html.num(e.utxos)).append("</td>");
      if (multi)
      {
        sb.append("<td>");
        for (int s : e.shards) sb.append(Html.shardBadge(s)).append(" ");
        sb.append("</td>");
      }
      sb.append("</tr>");
    }
    sb.append("</tbody></table></div>");
    sb.append(Html.pager("/richlist?", page, page < pages));
    sb.append("</div>");
    return sb.toString();
  }
}
