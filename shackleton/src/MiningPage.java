package snowblossom.shackleton;

import java.util.Map;
import snowblossom.lib.*;
import snowblossom.shackleton.ChainIndex.NetStats;
import snowblossom.shackleton.ChainIndex.ShardStats;

public class MiningPage
{
  public static String render(Shackleton sh)
  {
    MiningStats.Snapshot ms = sh.getMiningStats().getSnapshot();
    ChainIndex index = sh.getIndex();
    NetStats ns = index.getNetStats();
    Map<Integer, ShardStats> stats = index.getShardStats();
    NetworkParams params = sh.getParams();
    StringBuilder sb = new StringBuilder();

    if (ms == null)
    {
      sb.append("<div class='panel'><h1>Mining</h1><p class='muted'>Mining statistics are being collected from the last ").append(sh.getMiningStats().getLookBack()).append(" blocks of each shard, please check back shortly.</p></div>");
      return sb.toString();
    }

    sb.append("<div class='cards'>");
    sb.append(Html.card("Blocks analyzed", Html.num(ms.blocks), "last " + Html.num(ms.look_back) + " per shard · " + Html.duration(ms.newest_ts - ms.oldest_ts)));
    sb.append(Html.card("Pools / remarks", "" + ms.remarks.size(), "distinct coinbase remarks"));
    sb.append(Html.card("Miner addresses", "" + ms.miners.size(), "distinct reward recipients"));
    sb.append(Html.card("Rewards paid", Html.dec(ms.total_reward / 1e6, 2) + " <span class='muted small'>SNOW</span>", Html.snowShort(ms.total_fees) + " SNOW of it fees"));
    if (ns != null) sb.append(Html.card("Network hashrate", Html.hashrate(ns.hashrate), "difficulty " + Html.dec(ns.avg_diff, 3)));
    sb.append(Html.card("Updated", Html.time(ms.generated), "refreshed every minute"));
    sb.append("</div>");

    // pools
    sb.append("<div class='panel'><h2>Pools <span class='muted small'>by coinbase remark</span></h2>");
    sb.append("<div class='tblwrap'><table class='tbl'><thead><tr><th>Remark</th><th class='num'>Blocks</th><th>Share</th><th class='num'>Rewards</th><th class='num' title='estimated from block share'>Est. hashrate</th><th>Last block</th></tr></thead><tbody>");
    for (MiningStats.Entry e : ms.remarks)
    {
      sb.append("<tr><td><b>").append(Html.esc(e.name)).append("</b>");
      if (e.shards.size() > 1 || (ms.blocks_per_shard.size() > 1))
      {
        sb.append(" <span class='small'>");
        for (int s : e.shards) sb.append(Html.shardBadge(s)).append(" ");
        sb.append("</span>");
      }
      sb.append("</td><td class='num'>").append(e.blocks).append("</td><td>").append(Html.bar(e.share, "")).append(" <span class='small'>").append(Html.pct(e.share)).append("</span></td>");
      sb.append("<td class='num'>").append(Html.snowShort(e.reward)).append("</td>");
      sb.append("<td class='num'>").append(ns != null ? Html.hashrate(ns.hashrate * e.share) : "-").append("</td>");
      sb.append("<td>").append(Html.time(e.last_ts)).append("</td></tr>");
    }
    sb.append("</tbody></table></div></div>");

    // miners
    sb.append("<div class='panel'><h2>Miner addresses <span class='muted small'>coinbase recipients</span></h2>");
    sb.append("<div class='tblwrap'><table class='tbl'><thead><tr><th>Address</th><th class='num'>Blocks</th><th>Share</th><th class='num'>Rewards</th><th>Remarks</th></tr></thead><tbody>");
    int n = 0;
    for (MiningStats.Entry e : ms.miners)
    {
      sb.append("<tr><td>").append(Html.addrLink(e.name)).append("</td><td class='num'>").append(e.blocks).append("</td><td>").append(Html.bar(e.share, "")).append(" <span class='small'>").append(Html.pct(e.share)).append("</span></td>");
      sb.append("<td class='num'>").append(Html.snowShort(e.reward)).append("</td>");
      StringBuilder rem = new StringBuilder();
      int k = 0;
      for (String r : e.remarks)
      {
        if (k > 0) rem.append(", ");
        rem.append(Html.esc(r));
        k++;
        if (k >= 3)
        {
          if (e.remarks.size() > 3) rem.append(" …");
          break;
        }
      }
      sb.append("<td class='small wrap'>").append(rem).append("</td></tr>");
      n++;
      if (n >= 100) break;
    }
    sb.append("</tbody></table></div>");
    if (ms.miners.size() > 100) sb.append("<p class='muted small'>Showing the top 100 of ").append(ms.miners.size()).append(" addresses.</p>");
    sb.append("</div>");

    // per shard block counts
    if (ms.blocks_per_shard.size() > 1)
    {
      sb.append("<div class='panel'><h2>Blocks per shard in sample</h2><div class='tblwrap'><table class='tbl'><thead><tr><th>Shard</th><th class='num'>Blocks</th><th>Share</th></tr></thead><tbody>");
      for (Map.Entry<Integer, Integer> me : ms.blocks_per_shard.entrySet())
      {
        double share = (double) me.getValue() / ms.blocks;
        sb.append("<tr><td>").append(Html.shardBadge(me.getKey())).append("</td><td class='num'>").append(me.getValue()).append("</td><td>").append(Html.bar(share, "")).append(" ").append(Html.pct(share)).append("</td></tr>");
      }
      sb.append("</tbody></table></div></div>");
    }

    // votes
    sb.append("<div class='panel' id='votes'><h2>Motion votes</h2>");
    if (ms.votes.size() == 0)
    {
      sb.append("<p class='muted small'>No motions are being voted on in the analyzed blocks. Miners vote on Snowblossom Improvement Proposals by putting motion numbers into their coinbase transactions.</p>");
    }
    else
    {
      sb.append("<p class='muted small'>Miners vote on motions (Snowblossom Improvement Proposals) through their coinbase transactions. Counts over the last ").append(Html.num(ms.blocks)).append(" blocks.</p>");
      sb.append("<div class='tblwrap'><table class='tbl'><thead><tr><th>Motion</th><th class='num'>Yes</th><th class='num'>No</th><th class='num'>Both</th><th class='num'>Voting blocks</th><th>Approval</th></tr></thead><tbody>");
      for (Map.Entry<Integer, MiningStats.VoteCount> me : ms.votes.entrySet())
      {
        MiningStats.VoteCount vc = me.getValue();
        double yes_ratio = (vc.yes + vc.no == 0) ? 0 : (double) vc.yes / (vc.yes + vc.no);
        sb.append("<tr><td><b>").append(me.getKey()).append("</b></td><td class='num'>").append(vc.yes).append("</td><td class='num'>").append(vc.no).append("</td><td class='num'>").append(vc.both).append("</td><td class='num'>").append(vc.total()).append(" <span class='muted small'>(").append(Html.pct((double) vc.total() / ms.blocks)).append(" of blocks)</span></td>");
        sb.append("<td>").append(Html.bar(yes_ratio, yes_ratio >= 0.5 ? "ok" : "warn")).append(" ").append(Html.pct(yes_ratio)).append("</td></tr>");
      }
      sb.append("</tbody></table></div>");
    }
    sb.append("</div>");

    // calculator
    sb.append("<div class='panel' id='calc'><h2>Mining calculator</h2>");
    sb.append("<p class='muted small'>Expected rewards for a given hashrate once the difficulty has adjusted to it: your share of the blocks is your hashrate divided by the network hashrate including yours");
    if (stats.size() > 1) sb.append(" (of the selected shard)");
    sb.append(", and the network keeps producing one block per ").append(Html.duration(params.getBlockTimeTarget())).append(" on average. Fees are not included.</p>");
    sb.append("<div class='toolbar'>");
    if (stats.size() > 1)
    {
      sb.append("<label>Shard <select id='calcshard'>");
      for (ShardStats ss : stats.values())
      {
        sb.append("<option value='").append(ss.shard).append("' data-hashrate='").append(ss.hashrate).append("' data-reward='").append(ss.subsidy / Globals.SNOW_VALUE_D).append("'>shard ").append(ss.shard).append(" (").append(Html.hashrate(ss.hashrate)).append(")</option>");
      }
      sb.append("</select></label>");
    }
    double hashrate = 0;
    double reward = 0;
    if (stats.size() > 0)
    {
      ShardStats first = stats.values().iterator().next();
      hashrate = first.hashrate;
      reward = first.subsidy / Globals.SNOW_VALUE_D;
    }
    sb.append("<label>Hashrate <input class='calc' type='number' id='hashrate' value='1000' min='0' step='any' data-hashrate='").append(hashrate).append("' data-reward='").append(reward).append("' data-target='").append(params.getBlockTimeTarget() / 1000.0).append("'> kH/s</label>");
    sb.append("</div>");
    sb.append("<div class='tblwrap'><table class='tbl' style='max-width:420px'><tbody>");
    sb.append("<tr><td>Share of network hashrate</td><td class='num'><b id='calc0'>0</b> %</td></tr>");
    sb.append("<tr><td>Per hour</td><td class='num'><b id='calc1'>0</b> SNOW</td></tr>");
    sb.append("<tr><td>Per day</td><td class='num'><b id='calc2'>0</b> SNOW</td></tr>");
    sb.append("<tr><td>Per month (30 d)</td><td class='num'><b id='calc3'>0</b> SNOW</td></tr>");
    sb.append("</tbody></table></div>");
    sb.append("<script>document.addEventListener('DOMContentLoaded',function(){var e=document.getElementById('hashrate');if(e){e.dispatchEvent(new Event('input'))}});</script>");
    sb.append("</div>");
    return sb.toString();
  }
}
