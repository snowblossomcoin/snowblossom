package snowblossom.shackleton;

import java.util.List;
import java.util.Map;
import snowblossom.lib.*;
import snowblossom.shackleton.ChainIndex.NetStats;
import snowblossom.shackleton.ChainIndex.ShardStats;

public class HomePage
{
  public static final int BLOCKS_ON_HOME = 30;

  public static String render(Shackleton sh)
  {
    ChainIndex index = sh.getIndex();
    NetStats ns = index.getNetStats();
    StringBuilder sb = new StringBuilder();

    if ((ns == null) || (!index.isReady()))
    {
      sb.append("<div class='panel'><h2>Explorer starting up</h2><p>The chain index is being built, this page will be available shortly.</p>");
      if (index.getLastError() != null) sb.append("<p class='muted'>").append(Html.esc(index.getLastError())).append("</p>");
      sb.append("<p><a class='btn' href='/'>Reload</a></p></div>");
      return sb.toString();
    }

    Map<Integer, ShardStats> stats = index.getShardStats();
    NetworkParams params = sh.getParams();

    // ------------------------------------------------------------ KPI cards
    sb.append("<div class='cards'>\n");
    String height_sub;
    if (stats.size() > 1)
    {
      height_sub = stats.size() + " active shards · latest " + Html.time(ns.newest_block_ts);
    }
    else
    {
      height_sub = "latest block " + Html.time(ns.newest_block_ts);
    }
    sb.append(Html.cardLink("/blocks", "Block height", Html.num(ns.max_height), height_sub));
    sb.append(Html.card("Network hashrate", Html.hashrate(ns.hashrate), "difficulty " + Html.dec(ns.avg_diff, 3) + (stats.size() > 1 ? " (avg of shards)" : "")));
    sb.append(Html.card("Block time", Html.duration(ns.blocktime_avg_ms), "target " + Html.duration(params.getBlockTimeTarget()) + " · " + Html.num(ns.blocks_24h) + " blocks / 24h"));
    sb.append(Html.card("Transactions", Html.num(ns.tx_24h) + " <span class='muted small'>/ 24h</span>", Html.num(ns.total_transactions) + " total · " + Html.num(ns.tx_1h) + " last hour"));
    sb.append(Html.card("Fees 24h", Html.snow(ns.fees_24h) + " <span class='muted small'>SNOW</span>", Html.snowShort(ns.coinbase_24h) + " SNOW paid to miners"));

    RichList.Result rl = sh.getRichList().getResult();
    if (rl != null)
    {
      sb.append(Html.cardLink("/richlist", "Circulating supply", Html.dec(rl.total / 1e6, 0) + " <span class='muted small'>SNOW</span>",
        Html.num(rl.address_count) + " addresses · " + Html.num(rl.utxo_count) + " UTXOs"));
    }
    else
    {
      sb.append(Html.cardLink("/richlist", "Circulating supply", "<span class='muted'>computing…</span>", "rich list is being calculated"));
    }
    sb.append(Html.cardLink("/peers", "Mempool / Peers", Html.num(ns.mem_pool_size) + " <span class='muted small'>tx</span> · " + Html.num(ns.connected_peers),
      Html.num(ns.estimated_nodes) + " nodes estimated"));

    String field_sub;
    if (ns.next_field_name != null)
    {
      field_sub = Html.fieldProgress(ns, false) + " <span class='muted'>for " + Html.esc(ns.next_field_name) + " (" + Html.factor(ns.field_hashrate_factor) + " more)</span>";
    }
    else
    {
      field_sub = "highest field";
    }
    sb.append(Html.cardLink("/stats", "Snow field", "#" + ns.activated_field + " " + Html.esc(ns.field_name), field_sub));
    sb.append("</div>\n");

    // ------------------------------------------------------------ Shards
    sb.append("<div class='panel'><h2>Shards <a class='more' href='/shards'>Shard tree &rarr;</a></h2>");
    if (stats.size() == 1)
    {
      ShardStats only = stats.values().iterator().next();
      sb.append("<p class='muted small'>The network currently runs on a single shard (shard ").append(only.shard).append("). ");
      sb.append("A shard splits into two child shards once its blocks average more than ")
        .append(Html.bytes(params.getShardForkThreshold())).append(" of transaction data over at least ")
        .append(Html.num(params.getMinShardLength())).append(" blocks.</p>");
    }
    sb.append(ShardsPage.shardTable(sh, stats.values(), true));
    sb.append("</div>\n");

    // ------------------------------------------------------------ Blocks + side
    sb.append("<div class='grid3'>\n");
    sb.append("<div class='panel' data-autorefresh='/fragment/home-blocks'>");
    sb.append(latestBlocksInner(sh));
    sb.append("</div>\n");

    sb.append("<div>");
    // Network panel
    sb.append("<div class='panel'><h2>Network</h2><dl class='kv'>");
    sb.append(Html.kv("Network", Html.esc(Html.networkLabel(params)) + " <span class='muted'>(" + Html.esc(params.getNetworkName()) + ")</span>"));
    sb.append(Html.kv("Node version", Html.esc(ns.node_version)));
    sb.append(Html.kv("Connected peers", Html.num(ns.connected_peers)));
    sb.append(Html.kv("Estimated nodes", Html.num(ns.estimated_nodes)));
    sb.append(Html.kv("Block reward", Html.snow(ns.subsidy_per_height) + " SNOW <span class='muted small'>per height" + (stats.size() > 1 ? ", shared by shards" : "") + "</span>"));
    sb.append(Html.kv("Snow field", "#" + ns.activated_field + " " + Html.esc(ns.field_name)));
    if (ns.next_field_name != null)
    {
      sb.append(Html.kv("Next field", Html.esc(ns.next_field_name) + " at difficulty " + Html.dec(ns.next_field_diff, 0) + "<div class='small'>" + Html.fieldProgress(ns, true) + "</div>"));
    }
    sb.append(Html.kv("Max shards", Html.num(params.getMaxShardId() + 1) + " <span class='muted small'>(ids 0–" + params.getMaxShardId() + ")</span>"));
    sb.append("</dl>");
    if (ns.version_map.size() > 0)
    {
      sb.append("<h3 style='margin-top:14px'>Peer versions</h3><div class='tblwrap'><table class='tbl'><thead><tr><th>Version</th><th class='num'>Peers</th></tr></thead><tbody>");
      for (Map.Entry<String, Integer> me : ns.version_map.entrySet())
      {
        sb.append("<tr><td class='mono'>").append(Html.esc(HexUtil.getSafeString(me.getKey()))).append("</td><td class='num'>").append(me.getValue()).append("</td></tr>");
      }
      sb.append("</tbody></table></div>");
    }
    sb.append("</div>\n");

    // Mining panel
    MiningStats.Snapshot ms = sh.getMiningStats().getSnapshot();
    sb.append("<div class='panel'><h2>Mining <a class='more' href='/mining'>Details &rarr;</a></h2>");
    if (ms == null)
    {
      sb.append("<p class='muted small'>Mining statistics are being collected…</p>");
    }
    else
    {
      sb.append("<p class='muted small'>Pools by coinbase remark, last ").append(Html.num(ms.blocks)).append(" blocks</p>");
      sb.append("<div class='tblwrap'><table class='tbl'><thead><tr><th>Pool / remark</th><th class='num'>Blocks</th><th>Share</th></tr></thead><tbody>");
      int n = 0;
      for (MiningStats.Entry e : ms.remarks)
      {
        sb.append("<tr><td class='wrap'>").append(Html.esc(e.name)).append("</td><td class='num'>").append(e.blocks).append("</td><td>")
          .append(Html.bar(e.share, "")).append(" <span class='small muted'>").append(Html.pct(e.share)).append("</span></td></tr>");
        n++;
        if (n >= 6) break;
      }
      sb.append("</tbody></table></div>");
      if (ms.votes.size() > 0)
      {
        sb.append("<p class='small' style='margin:10px 0 0'><a href='/mining#votes'>").append(ms.votes.size()).append(" motion(s) being voted on</a></p>");
      }
    }
    sb.append("</div>\n");

    // Rich list summary
    if (rl != null)
    {
      sb.append("<div class='panel'><h2>Distribution <a class='more' href='/richlist'>Rich list &rarr;</a></h2><dl class='kv'>");
      sb.append(Html.kv("Top 10 hold", Html.pct((double) rl.top10 / rl.total)));
      sb.append(Html.kv("Top 100 hold", Html.pct((double) rl.top100 / rl.total)));
      sb.append(Html.kv("Top 1000 hold", Html.pct((double) rl.top1000 / rl.total)));
      sb.append(Html.kv("Updated", Html.time(rl.generated)));
      sb.append("</dl></div>\n");
    }
    sb.append("</div>\n"); // side column
    sb.append("</div>\n"); // grid

    return sb.toString();
  }

  /** Inner html of the latest blocks panel (used for auto refresh) */
  public static String latestBlocksInner(Shackleton sh)
  {
    ChainIndex index = sh.getIndex();
    List<BlockInfo> recent = index.getRecentAll();
    boolean multi = index.getShardStats().size() > 1;
    StringBuilder sb = new StringBuilder();
    sb.append("<h2>Latest blocks <a class='more' href='/blocks'>All blocks &rarr;</a></h2>");
    sb.append("<div class='tblwrap'><table class='tbl' id='blocktable'>");
    sb.append(BlocksPage.tableHead(multi, true));
    sb.append("<tbody>");
    int n = 0;
    java.util.TreeMap<Integer, Integer> cursor = new java.util.TreeMap<>();
    for (ChainIndex.ShardStats ss : index.getShardStats().values()) cursor.put(ss.shard, ss.height);
    for (BlockInfo bi : recent)
    {
      sb.append(BlocksPage.row(bi, multi, true));
      Integer cur = cursor.get(bi.shard);
      if ((cur == null) || (cur > bi.height - 1)) cursor.put(bi.shard, bi.height - 1);
      n++;
      if (n >= BLOCKS_ON_HOME) break;
    }
    sb.append("</tbody></table></div>");
    if ((n > 0) && (cursor.size() > 0))
    {
      sb.append("<div class='pager'><button class='btn' id='loadmore' data-url='/blocks?cursor=").append(BlocksPage.cursorString(cursor)).append("&fragment=1&compact=1'>Load more</button></div>");
    }
    return sb.toString();
  }
}
