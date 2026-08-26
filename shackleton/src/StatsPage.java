package snowblossom.shackleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import snowblossom.lib.*;
import snowblossom.shackleton.ChainIndex.NetStats;
import snowblossom.shackleton.ChainIndex.ShardStats;
import snowblossom.shackleton.HistoryStats.Bucket;

public class StatsPage
{
  public interface Fmt
  {
    String format(double v);
  }

  public static class Series
  {
    public final String name;
    public final String color;
    public final double[] values;
    public Series(String name, String color, double[] values)
    {
      this.name = name;
      this.color = color;
      this.values = values;
    }
  }

  public static String render(Shackleton sh, Map<String, String> q)
  {
    ChainIndex index = sh.getIndex();
    NetStats ns = index.getNetStats();
    NetworkParams params = sh.getParams();
    StringBuilder sb = new StringBuilder();

    if (ns == null)
    {
      return "<div class='panel'><h1>Statistics</h1><p class='muted'>The chain index is still being built.</p></div>";
    }

    sb.append("<div class='cards'>");
    sb.append(Html.card("Total transactions", Html.num(ns.total_transactions), "across all shards"));
    sb.append(Html.card("Blocks / 24h", Html.num(ns.blocks_24h), Html.num(ns.blocks_1h) + " in the last hour"));
    sb.append(Html.card("Transactions / 24h", Html.num(ns.tx_24h), Html.dec(ns.tx_24h / 86400.0, 4) + " tx/s"));
    sb.append(Html.card("Block data / 24h", Html.bytes(ns.size_24h), "serialized block size"));
    sb.append(Html.card("Fees / 24h", Html.snowShort(ns.fees_24h) + " <span class='muted small'>SNOW</span>", "avg " + (ns.tx_24h > 0 ? Html.snow(ns.fees_24h / Math.max(1, ns.tx_24h)) : "0") + " per tx"));
    sb.append(Html.card("Coinbase / 24h", Html.snowShort(ns.coinbase_24h) + " <span class='muted small'>SNOW</span>", "subsidy + fees paid to miners"));
    sb.append("</div>");

    // snow fields
    sb.append("<div class='panel'><h2>Snow fields</h2>");
    sb.append("<p class='muted small'>Snowblossom's proof of work reads from large precomputed \"snow fields\". A higher field activates once the average difficulty passes its activation difficulty; miners then need the larger field. Difficulty is logarithmic: one unit means twice the hashrate.</p>");
    sb.append("<div class='tblwrap'><table class='tbl'><thead><tr><th class='num'>#</th><th>Name</th><th class='num'>Size</th><th class='num'>Activation difficulty</th><th>Status</th></tr></thead><tbody>");
    for (int f = 0; f < 64; f++)
    {
      SnowFieldInfo sf = params.getSnowFieldInfo(f);
      if (sf == null) break;
      double act = PowUtil.getDiffForTarget(sf.getActivationTarget());
      String status;
      if (f < ns.activated_field) status = Html.pill("", "retired");
      else if (f == ns.activated_field) status = Html.pill("ok", "active");
      else if (f == ns.activated_field + 1) status = Html.pill("accent", "next") + " " + Html.fieldProgress(ns, false);
      else status = "";
      sb.append("<tr><td class='num'>").append(f).append("</td><td>").append(Html.esc(sf.getName())).append("</td><td class='num'>").append(Html.bytes(sf.getLength())).append("</td><td class='num'>").append(Html.dec(act, 0)).append("</td><td>").append(status).append("</td></tr>");
    }
    sb.append("</tbody></table></div><p class='muted small' style='margin:10px 0 0'>").append(Html.fieldExplanation(ns)).append("</p></div>");

    String[] rdef = HistoryStats.findRange(q.getOrDefault("range", "30d"));
    String range_key = rdef[0];
    long range_ms = Long.parseLong(rdef[1]);
    long bucket_ms = Long.parseLong(rdef[2]);
    HistoryStats hs = sh.getHistoryStats();

    StringBuilder tabs = new StringBuilder();
    tabs.append("<div class='tabs'>");
    for (String[] r : HistoryStats.RANGES)
    {
      tabs.append("<a href='/stats?range=").append(r[0]).append("#history'").append(r[0].equals(range_key) ? " class='active'" : "").append(">").append(r[3]).append("</a>");
    }
    tabs.append("</div>");

    if (!hs.isReady())
    {
      sb.append("<div class='panel' id='history'><h2>History</h2>").append(tabs).append("<p class='muted'>The block history is being collected (up to ").append(hs.getDays()).append(" days of headers), please check back in a minute.</p></div>");
      return sb.toString();
    }
    HistoryStats.Series hist = hs.series(range_ms, bucket_ms);

    int n = hist.starts.size();
    String[] labels = new String[n];
    for (int i = 0; i < n; i++) labels[i] = bucketLabel(hist.starts.get(i), bucket_ms, false);

    boolean multi = hist.per_shard.size() > 1;
    List<Series> hr = new ArrayList<>();
    List<Series> blocks = new ArrayList<>();
    List<Series> txs = new ArrayList<>();
    List<Series> diffs = new ArrayList<>();

    double[] t_hr = new double[n];
    double[] t_blocks = new double[n];
    double[] t_txs = new double[n];
    double[] t_diff = new double[n];
    for (int i = 0; i < n; i++)
    {
      Bucket d = hist.total.get(i);
      t_hr[i] = d.hashrate();
      t_blocks[i] = d.blocks;
      t_txs[i] = d.txs;
      t_diff[i] = d.avgDiff();
    }
    String total_color = "var(--accent)";
    if (multi)
    {
      for (Map.Entry<Integer, ? extends List<Bucket>> me : hist.per_shard.entrySet())
      {
        int shard = me.getKey();
        double[] s_hr = new double[n];
        double[] s_blocks = new double[n];
        double[] s_txs = new double[n];
        double[] s_diff = new double[n];
        for (int i = 0; i < n; i++)
        {
          Bucket d = me.getValue().get(i);
          s_hr[i] = d.hashrate();
          s_blocks[i] = d.blocks;
          s_txs[i] = d.txs;
          s_diff[i] = d.avgDiff();
        }
        String c = Html.shardColor(shard);
        hr.add(new Series("shard " + shard, c, s_hr));
        blocks.add(new Series("shard " + shard, c, s_blocks));
        txs.add(new Series("shard " + shard, c, s_txs));
        diffs.add(new Series("shard " + shard, c, s_diff));
      }
      hr.add(new Series("network", total_color, t_hr));
      blocks.add(new Series("all shards", total_color, t_blocks));
      txs.add(new Series("all shards", total_color, t_txs));
    }
    else
    {
      hr.add(new Series("network", total_color, t_hr));
      blocks.add(new Series("blocks", total_color, t_blocks));
      txs.add(new Series("transactions", total_color, t_txs));
      diffs.add(new Series("difficulty", total_color, t_diff));
    }

    String per = rdef[4];
    sb.append("<div class='panel' id='history'><h2>History <span class='muted small'>last ").append(rdef[3]).append(", ").append(per).append(" buckets</span></h2>").append(tabs);
    sb.append("<p class='muted small'>Computed from block headers (").append(Html.num(hs.recordCount())).append(" blocks over ").append(hs.getDays()).append(" days kept in memory), refreshed every 5 minutes; last update ").append(Html.time(hist.generated)).append(". The last bucket is partial.</p></div>");

    sb.append("<div class='grid2'>");
    sb.append("<div class='panel'><h2>Network hashrate <span class='muted small'>").append(per).append("</span></h2>").append(lineChart(labels, hr, new Fmt() { public String format(double v) { return Html.hashrate(v); } }, false)).append("</div>");
    sb.append("<div class='panel'><h2>Difficulty <span class='muted small'>").append(per).append(" average</span></h2>").append(lineChart(labels, diffs, new Fmt() { public String format(double v) { return Html.dec(v, 2); } }, true)).append("</div>");
    // counts: leave out the current, partial bucket (rates above are normalized and keep it)
    String[] labels_c = labels;
    List<Series> blocks_c = blocks;
    List<Series> txs_c = txs;
    if (n > 2)
    {
      labels_c = java.util.Arrays.copyOf(labels, n - 1);
      blocks_c = new ArrayList<>();
      txs_c = new ArrayList<>();
      for (Series x : blocks) blocks_c.add(new Series(x.name, x.color, java.util.Arrays.copyOf(x.values, n - 1)));
      for (Series x : txs) txs_c.add(new Series(x.name, x.color, java.util.Arrays.copyOf(x.values, n - 1)));
    }
    sb.append("<div class='panel'><h2>Blocks <span class='muted small'>").append(per).append(", completed buckets</span></h2>").append(lineChart(labels_c, blocks_c, new Fmt() { public String format(double v) { return Html.dec(v, 0); } }, false)).append("</div>");
    sb.append("<div class='panel'><h2>Transactions <span class='muted small'>").append(per).append(", completed buckets</span></h2>").append(lineChart(labels_c, txs_c, new Fmt() { public String format(double v) { return Html.dec(v, 0); } }, false)).append("</div>");
    sb.append("</div>");

    sb.append("<div class='panel'><h2>Totals <span class='muted small'>").append(per).append("</span></h2>");
    sb.append("<div class='tblwrap'><table class='tbl'><thead><tr><th>Period (UTC)</th><th class='num'>Blocks</th><th class='num'>Transactions</th><th class='num'>Tx data</th><th class='num'>Avg block time</th><th class='num'>Avg difficulty</th><th class='num'>Hashrate</th>");
    if (multi) for (int s : hist.per_shard.keySet()) sb.append("<th class='num'>S").append(s).append(" blocks</th>");
    sb.append("</tr></thead><tbody>");
    for (int i = n - 1; i >= 0; i--)
    {
      Bucket d = hist.total.get(i);
      sb.append("<tr><td>").append(bucketLabel(d.start, bucket_ms, true)).append(i == n - 1 ? " <span class='muted small'>(current)</span>" : "").append("</td>");
      sb.append("<td class='num'>").append(Html.num(d.blocks)).append("</td><td class='num'>").append(Html.num(d.txs)).append("</td><td class='num'>").append(Html.bytes(d.tx_data)).append("</td>");
      sb.append("<td class='num'>").append(d.blocks > 0 ? Html.duration((long) (d.avgBlockTimeSec() * 1000)) : "-").append("</td>");
      sb.append("<td class='num'>").append(d.blocks > 0 ? Html.dec(d.avgDiff(), 3) : "-").append("</td><td class='num'>").append(Html.hashrate(d.hashrate())).append("</td>");
      if (multi)
      {
        for (List<Bucket> lst : hist.per_shard.values())
        {
          sb.append("<td class='num'>").append(lst.get(i).blocks).append("</td>");
        }
      }
      sb.append("</tr>");
    }
    sb.append("</tbody></table></div></div>");

    // active shard table
    Map<Integer, ShardStats> stats = index.getShardStats();
    sb.append("<div class='panel'><h2>Current shard statistics</h2>").append(ShardsPage.shardTable(sh, stats.values(), false)).append("</div>");
    return sb.toString();
  }

  /** Label for a bucket start: hours for sub-day buckets, days otherwise; long form adds the end for multi-day buckets */
  public static String bucketLabel(long start, long bucket_ms, boolean full)
  {
    if (bucket_ms < HistoryStats.DAY_MS)
    {
      if (full) return Html.dateShort(start);
      if (bucket_ms <= HistoryStats.HOUR_MS) return Html.dateShort(start).substring(11);
      return Html.dateShort(start).substring(5);
    }
    if (bucket_ms == HistoryStats.DAY_MS) return full ? Html.day(start) : Html.day(start).substring(5);
    long end = start + bucket_ms - HistoryStats.DAY_MS;
    if (full) return Html.day(start) + " – " + Html.day(end);
    return Html.day(start).substring(5);
  }

  /** Simple responsive SVG line chart */
  public static String lineChart(String[] labels, List<Series> series, Fmt fmt, boolean zoom_y)
  {
    int n = labels.length;
    int w = 640;
    int h = 240;
    int pl = 64, pr = 12, pt = 12, pb = 28;
    double max = 0;
    double min = Double.MAX_VALUE;
    for (Series s : series)
    {
      for (double v : s.values)
      {
        if (Double.isNaN(v) || Double.isInfinite(v)) continue;
        max = Math.max(max, v);
        min = Math.min(min, v);
      }
    }
    if (min == Double.MAX_VALUE) min = 0;
    if (!zoom_y) min = 0;
    if (max <= min) max = min + 1;
    // headroom
    double range = max - min;
    max = max + range * 0.08;
    if (zoom_y) min = Math.max(0, min - range * 0.08);

    StringBuilder sb = new StringBuilder();
    sb.append("<div class='chart'><svg viewBox='0 0 ").append(w).append(" ").append(h).append("' xmlns='http://www.w3.org/2000/svg'>");
    // grid + y labels
    int grid = 4;
    for (int g = 0; g <= grid; g++)
    {
      double v = min + (max - min) * g / grid;
      double y = pt + (h - pt - pb) * (1 - (v - min) / (max - min));
      sb.append("<line class='axis' x1='").append(pl).append("' x2='").append(w - pr).append("' y1='").append(r(y)).append("' y2='").append(r(y)).append("'/>");
      sb.append("<text x='").append(pl - 6).append("' y='").append(r(y + 4)).append("' text-anchor='end'>").append(Html.esc(fmt.format(v))).append("</text>");
    }
    // x labels
    int step = Math.max(1, n / 8);
    for (int i = 0; i < n; i += step)
    {
      double x = pl + (w - pl - pr) * ((n == 1) ? 0.5 : ((double) i / (n - 1)));
      sb.append("<text x='").append(r(x)).append("' y='").append(h - 8).append("' text-anchor='middle'>").append(Html.esc(labels[i])).append("</text>");
    }
    // series
    for (Series s : series)
    {
      StringBuilder path = new StringBuilder();
      for (int i = 0; i < n; i++)
      {
        double v = s.values[i];
        if (Double.isNaN(v) || Double.isInfinite(v)) v = 0;
        double x = pl + (w - pl - pr) * ((n == 1) ? 0.5 : ((double) i / (n - 1)));
        double y = pt + (h - pt - pb) * (1 - (v - min) / (max - min));
        path.append(i == 0 ? "M" : "L").append(r(x)).append(" ").append(r(y)).append(" ");
      }
      sb.append("<path d='").append(path).append("' fill='none' stroke='").append(s.color).append("' stroke-width='2' stroke-linejoin='round' stroke-linecap='round'/>");
      // last point marker + tooltip circles
      for (int i = 0; i < n; i++)
      {
        double v = s.values[i];
        double x = pl + (w - pl - pr) * ((n == 1) ? 0.5 : ((double) i / (n - 1)));
        double y = pt + (h - pt - pb) * (1 - (v - min) / (max - min));
        sb.append("<circle cx='").append(r(x)).append("' cy='").append(r(y)).append("' r='").append(i == n - 1 ? 3 : 2.2).append("' fill='").append(s.color).append("'><title>").append(Html.esc(labels[i])).append(" · ").append(Html.esc(s.name)).append(": ").append(Html.esc(fmt.format(v))).append("</title></circle>");
      }
    }
    sb.append("</svg>");
    if (series.size() > 1)
    {
      sb.append("<div class='legend'>");
      for (Series s : series)
      {
        sb.append("<span><i style='background:").append(s.color).append("'></i>").append(Html.esc(s.name)).append("</span>");
      }
      sb.append("</div>");
    }
    sb.append("</div>");
    return sb.toString();
  }

  private static String r(double v)
  {
    return String.format(java.util.Locale.ROOT, "%.1f", v);
  }
}
