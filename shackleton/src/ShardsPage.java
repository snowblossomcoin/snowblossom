package snowblossom.shackleton;

import java.util.Collection;
import java.util.Map;
import snowblossom.lib.*;
import snowblossom.shackleton.ChainIndex.NetStats;
import snowblossom.shackleton.ChainIndex.ShardNode;
import snowblossom.shackleton.ChainIndex.ShardStats;
import snowblossom.shackleton.ChainIndex.ShardTree;

public class ShardsPage
{
  /** Table of live shard statistics, shared between the home page and the shards page */
  public static String shardTable(Shackleton sh, Collection<ShardStats> shards, boolean compact)
  {
    StringBuilder sb = new StringBuilder();
    sb.append("<div class='tblwrap'><table class='tbl'><thead><tr>");
    sb.append("<th>Shard</th><th class='num'>Height</th><th>Last block</th><th class='num' title='blocks in the last hour / 24 hours'>Blocks 1h / 24h</th><th class='num'>Txs 24h</th><th class='num'>Block time</th><th class='num'>Difficulty</th><th class='num'>Hashrate</th>");
    if (!compact) sb.append("<th class='num'>Subsidy</th>");
    sb.append("<th>Split readiness</th><th>Status</th></tr></thead><tbody>");
    for (ShardStats ss : shards)
    {
      sb.append("<tr>");
      sb.append("<td>").append(Html.shardBadge(ss.shard)).append(" <span class='muted small'>gen ").append(ss.generation).append("</span></td>");
      sb.append("<td class='num'>").append(Html.blockHeightLink(ss.shard, ss.height)).append("</td>");
      sb.append("<td>").append(Html.time(ss.timestamp)).append("</td>");
      sb.append("<td class='num'>").append(ss.blocks_1h).append(" / ").append(ss.blocks_24h).append("</td>");
      sb.append("<td class='num'>").append(Html.num(ss.tx_24h)).append("</td>");
      sb.append("<td class='num'>").append(Html.duration(ss.blocktime_avg_ms)).append("</td>");
      sb.append("<td class='num' title='current target ").append(Html.dec(ss.target_diff, 3)).append("'>").append(Html.dec(ss.avg_diff, 3)).append("</td>");
      sb.append("<td class='num'>").append(Html.hashrate(ss.hashrate)).append("</td>");
      if (!compact) sb.append("<td class='num'>").append(Html.snowShort(ss.subsidy)).append("</td>");
      sb.append("<td>");
      if (!ss.can_split)
      {
        sb.append("<span class='muted small'>max shard depth</span>");
      }
      else
      {
        sb.append("<span class='small nowrap' title='average transaction data per block vs. the fork threshold'>size ").append(Html.bar(ss.split_size_ratio, ss.split_size_ratio >= 1 ? "warn" : "")).append(" ").append(Html.pct(ss.split_size_ratio)).append("</span><br>");
        sb.append("<span class='small nowrap' title='blocks in this shard vs. minimum shard length'>length ").append(Html.bar(ss.split_len_ratio, "ok")).append(" ").append(Html.pct(Math.min(1.0, ss.split_len_ratio))).append("</span>");
      }
      sb.append("</td>");
      sb.append("<td>");
      if (ss.coordinator) sb.append(Html.pill("accent", "coordinator")).append(" ");
      if (ss.isSplitReady()) sb.append(Html.pill("warn", "splitting")).append(" ");
      sb.append(Html.pill("ok", "active"));
      sb.append("</td>");
      sb.append("</tr>\n");
    }
    sb.append("</tbody></table></div>");
    return sb.toString();
  }

  public static String render(Shackleton sh)
  {
    ChainIndex index = sh.getIndex();
    NetworkParams params = sh.getParams();
    ShardTree tree = index.getShardTree();
    Map<Integer, ShardStats> stats = index.getShardStats();
    NetStats ns = index.getNetStats();
    StringBuilder sb = new StringBuilder();

    if ((tree == null) || (ns == null))
    {
      return "<div class='panel'><h1>Shards</h1><p class='muted'>The chain index is still being built.</p></div>";
    }

    int retired = 0;
    for (ShardNode n : tree.nodes.values()) if (n.isRetired()) retired++;

    sb.append("<div class='cards'>");
    sb.append(Html.card("Active shards", "" + tree.leaves.size(), retired > 0 ? retired + " shard(s) split into children" : "no shard has split yet"));
    sb.append(Html.card("Max generation", "" + tree.max_generation, "up to " + (params.getMaxShardId() + 1) + " shard ids allowed"));
    sb.append(Html.card("Fork threshold", Html.bytes(params.getShardForkThreshold()), "avg tx data per block to trigger a split"));
    sb.append(Html.card("Min shard length", Html.num(params.getMinShardLength()) + " blocks", "before a shard may split"));
    sb.append(Html.card("Shards activated", "height " + Html.num(params.getActivationHeightShards()), "SIP-5"));
    sb.append(Html.card("Combined hashrate", Html.hashrate(ns.hashrate), Html.num(ns.blocks_24h) + " blocks / 24h on all shards"));
    sb.append("</div>");

    sb.append("<div class='panel'><h2>Shard tree</h2>");
    sb.append("<p class='muted small'>Shards form a binary tree. When a shard splits, its two children continue the chain in parallel; the left child inherits the outputs of the parent. ");
    sb.append("Blocks of each shard import blocks of the other shards so that coins can move between shards. Shard 0 and its left descendants are the coordinator chain.</p>");
    sb.append(treeSvg(sh, tree));
    sb.append("</div>");

    sb.append("<div class='panel'><h2>Active shards</h2>");
    sb.append(shardTable(sh, stats.values(), false));
    sb.append("</div>");

    sb.append("<div class='panel'><h2>All known shards</h2>");
    sb.append("<div class='tblwrap'><table class='tbl'><thead><tr><th>Shard</th><th class='num'>Gen</th><th>Parent</th><th>Children</th><th>Status</th><th class='num'>First block</th><th class='num'>Last block</th><th class='num'>Blocks</th><th>Started</th><th>Ended</th></tr></thead><tbody>");
    for (ShardNode n : tree.nodes.values())
    {
      sb.append("<tr><td>").append(Html.shardBadge(n.id)).append("</td><td class='num'>").append(n.generation).append("</td>");
      sb.append("<td>").append(n.parent >= 0 ? Html.shardBadge(n.parent) : "<span class='muted'>–</span>").append("</td>");
      sb.append("<td>");
      for (int c : n.children) sb.append(Html.shardBadge(c)).append(" ");
      if (n.children.size() == 0)
      {
        if (ShardUtil.getShardChildIdRight(n.id) <= params.getMaxShardId())
        {
          sb.append("<span class='muted small' title='would become S").append(ShardUtil.getShardChildIdLeft(n.id)).append(" and S").append(ShardUtil.getShardChildIdRight(n.id)).append(" on a split'>not split</span>");
        }
        else sb.append("<span class='muted small'>max depth</span>");
      }
      sb.append("</td>");
      sb.append("<td>");
      if (n.active) sb.append(Html.pill("ok", "active"));
      else if (n.isRetired()) sb.append(Html.pill("", "split"));
      else sb.append(Html.pill("", "unknown"));
      sb.append("</td>");
      sb.append("<td class='num'>").append(n.first_height >= 0 ? (n.first_hash != null ? "<a href='/block/" + n.first_hash + "'>" + Html.num(n.first_height) + "</a>" : Html.num(n.first_height)) : "?").append("</td>");
      sb.append("<td class='num'>").append(n.last_height >= 0 ? (n.last_hash != null ? "<a href='/block/" + n.last_hash + "'>" + Html.num(n.last_height) + "</a>" : Html.num(n.last_height)) : "?").append("</td>");
      sb.append("<td class='num'>").append(n.getBlockCount() >= 0 ? Html.num(n.getBlockCount()) : "?").append("</td>");
      sb.append("<td>").append(n.first_ts > 0 ? Html.dateShort(n.first_ts) : "").append("</td>");
      sb.append("<td>").append((!n.active && n.last_ts > 0) ? Html.dateShort(n.last_ts) : (n.active ? "<span class='muted'>ongoing</span>" : "")).append("</td>");
      sb.append("</tr>");
    }
    sb.append("</tbody></table></div></div>");
    return sb.toString();
  }

  public static String treeSvg(Shackleton sh, ShardTree tree)
  {
    int gens = tree.max_generation + 1;
    int width = 1000;
    int row_h = 84;
    int height = gens * row_h + 20;
    StringBuilder sb = new StringBuilder();
    sb.append("<div class='tree'><svg viewBox='0 0 ").append(width).append(" ").append(height).append("' xmlns='http://www.w3.org/2000/svg' role='img' aria-label='Shard tree'>");

    // lines first
    for (ShardNode n : tree.nodes.values())
    {
      if (n.parent < 0) continue;
      ShardNode p = tree.nodes.get(n.parent);
      if (p == null) continue;
      double[] a = pos(n, width, row_h);
      double[] b = pos(p, width, row_h);
      sb.append("<line x1='").append(b[0]).append("' y1='").append(b[1]).append("' x2='").append(a[0]).append("' y2='").append(a[1]).append("'/>");
    }
    for (ShardNode n : tree.nodes.values())
    {
      double[] p = pos(n, width, row_h);
      double r = 20;
      String color = Html.shardColor(n.id);
      sb.append("<a href='/shard/").append(n.id).append("'>");
      sb.append("<circle cx='").append(p[0]).append("' cy='").append(p[1]).append("' r='").append(r).append("' fill='").append(color).append("'");
      if (!n.active) sb.append(" opacity='0.45' stroke='").append(color).append("' stroke-dasharray='4 3'");
      sb.append("/>");
      sb.append("<text x='").append(p[0]).append("' y='").append(p[1] + 4).append("' text-anchor='middle'>S").append(n.id).append("</text>");
      String lab;
      if (n.active) lab = "height " + Html.num(n.last_height);
      else if (n.last_height >= 0) lab = "split at " + Html.num(n.last_height);
      else lab = "";
      sb.append("<text class='lab' x='").append(p[0]).append("' y='").append(p[1] + r + 14).append("' text-anchor='middle'>").append(lab).append("</text>");
      if ((n.first_height > 0) && (n.active))
      {
        sb.append("<text class='lab' x='").append(p[0]).append("' y='").append(p[1] + r + 27).append("' text-anchor='middle'>from ").append(Html.num(n.first_height)).append("</text>");
      }
      sb.append("</a>");
    }
    sb.append("</svg></div>");
    return sb.toString();
  }

  private static double[] pos(ShardNode n, int width, int row_h)
  {
    int g = n.generation;
    int first_in_gen = (1 << g) - 1;
    int idx = n.id - first_in_gen;
    int count = 1 << g;
    double x = (idx + 0.5) / count * width;
    double y = g * row_h + 34;
    return new double[]{x, y};
  }

  public static String renderShard(Shackleton sh, int shard, Map<String, String> q)
    throws Exception
  {
    ChainIndex index = sh.getIndex();
    NetworkParams params = sh.getParams();
    ShardStats ss = index.getShardStats(shard);
    ShardTree tree = index.getShardTree();
    ShardNode node = (tree == null) ? null : tree.nodes.get(shard);
    if ((ss == null) && (node == null))
    {
      if ((shard < 0) || (shard > params.getMaxShardId()))
      {
        throw new WebUtil.NotFoundException("Shard " + shard + " does not exist on this network (max shard id " + params.getMaxShardId() + ").");
      }
      throw new WebUtil.NotFoundException("Shard " + shard + " has not been created yet.");
    }

    StringBuilder sb = new StringBuilder();
    sb.append("<div class='panel'><h1>Shard ").append(Html.shardBadge(shard));
    if (ss != null)
    {
      sb.append(" ").append(Html.pill("ok", "active"));
      if (ss.coordinator) sb.append(" ").append(Html.pill("accent", "coordinator"));
      if (ss.isSplitReady()) sb.append(" ").append(Html.pill("warn", "about to split"));
    }
    else if (node != null && node.isRetired())
    {
      sb.append(" ").append(Html.pill("", "split into children"));
    }
    sb.append("<span class='more'><a class='btn' href='/shards'>All shards</a> <a class='btn' href='/blocks?shard=").append(shard).append("'>Blocks</a></span></h1>");

    sb.append("<div class='grid2'><dl class='kv'>");
    sb.append(Html.kv("Generation", "" + ShardUtil.getShardGeneration(shard)));
    if (shard > 0) sb.append(Html.kv("Parent", Html.shardBadge(ShardUtil.getShardParentId(shard))));
    StringBuilder kids = new StringBuilder();
    boolean any_exists = false;
    for (int c : ShardUtil.getShardChildIds(shard))
    {
      if (c <= params.getMaxShardId())
      {
        boolean exists = (node != null) && node.children.contains(c);
        if (exists)
        {
          any_exists = true;
          kids.append(Html.shardBadge(c)).append(" ");
        }
        else
        {
          kids.append("<span style='opacity:.55'>").append(Html.shardBadge(c)).append("</span> ");
        }
      }
    }
    if (kids.length() == 0) sb.append(Html.kv("Children", "<span class='muted'>none possible (maximum shard depth reached)</span>"));
    else if (any_exists) sb.append(Html.kv("Children", kids.toString()));
    else sb.append(Html.kv("Children", kids.toString() + "<span class='muted small'>not created yet – this shard has not split; these ids would be used if it does</span>"));
    sb.append(Html.kv("Reward share", "1/" + ShardUtil.getShardShareDivisor(shard) + " of the subsidy per height"));
    if (node != null)
    {
      sb.append(Html.kv("First block", node.first_height >= 0 ? (node.first_hash != null ? "<a href='/block/" + node.first_hash + "'>" + Html.num(node.first_height) + "</a>" : Html.num(node.first_height)) + (node.first_ts > 0 ? " <span class='muted small'>" + Html.dateShort(node.first_ts) + "</span>" : "") : "?"));
      if (!node.active && node.last_height >= 0)
      {
        sb.append(Html.kv("Last block", (node.last_hash != null ? "<a href='/block/" + node.last_hash + "'>" + Html.num(node.last_height) + "</a>" : Html.num(node.last_height)) + (node.last_ts > 0 ? " <span class='muted small'>" + Html.dateShort(node.last_ts) + "</span>" : "")));
      }
      if (node.getBlockCount() >= 0) sb.append(Html.kv("Blocks in shard", Html.num(node.getBlockCount())));
    }
    sb.append("</dl>");

    if (ss != null)
    {
      sb.append("<dl class='kv'>");
      sb.append(Html.kv("Height", Html.blockHeightLink(shard, ss.height) + " · " + Html.blockLink(ss.head_info.hash)));
      sb.append(Html.kv("Last block", Html.timeFull(ss.timestamp)));
      sb.append(Html.kv("Blocks", ss.blocks_1h + " last hour · " + ss.blocks_24h + " last 24h"));
      sb.append(Html.kv("Transactions", Html.num(ss.tx_1h) + " last hour · " + Html.num(ss.tx_24h) + " last 24h · " + Html.num(ss.total_transactions) + " total in chain"));
      sb.append(Html.kv("Fees 24h", Html.snow(ss.fees_24h) + " SNOW"));
      sb.append(Html.kv("Block time", Html.duration(ss.blocktime_avg_ms) + " <span class='muted small'>(average)</span>"));
      sb.append(Html.kv("Difficulty", Html.dec(ss.avg_diff, 3) + " <span class='muted small'>avg · target " + Html.dec(ss.target_diff, 3) + "</span>"));
      sb.append(Html.kv("Hashrate", Html.hashrate(ss.hashrate)));
      sb.append(Html.kv("Subsidy per block", Html.snow(ss.subsidy) + " SNOW"));
      sb.append(Html.kv("Snow field", "#" + ss.activated_field + " " + Html.esc(BlockPage.fieldName(params, ss.activated_field))));
      sb.append(Html.kv("Avg tx data / block", Html.bytes(ss.tx_size_average) + " <span class='muted small'>of " + Html.bytes(params.getShardForkThreshold()) + " fork threshold (" + Html.pct(ss.split_size_ratio) + ")</span>"));
      sb.append(Html.kv("Shard length", Html.num(ss.shard_length) + " blocks <span class='muted small'>(min " + Html.num(params.getMinShardLength()) + " to split)</span>"));
      sb.append(Html.kv("Work sum", "<span class='mono small'>" + Html.esc(ss.work_sum) + "</span>"));
      sb.append("</dl>");
    }
    sb.append("</div></div>");

    if (ss != null)
    {
      sb.append(BlocksPage.renderShard(sh, shard, q, false));
    }
    else if ((node != null) && (node.last_hash != null))
    {
      Map<String, String> q2 = new java.util.HashMap<>(q);
      if (!q2.containsKey("from")) q2.put("from", "" + node.last_height);
      sb.append(BlocksPage.renderShard(sh, shard, q2, false));
    }
    return sb.toString();
  }
}
