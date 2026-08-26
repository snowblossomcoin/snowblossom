package snowblossom.shackleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import snowblossom.lib.*;
import snowblossom.shackleton.ChainIndex.ShardStats;

public class BlocksPage
{
  public static final int PAGE_SIZE = 50;

  public static String tableHead(boolean multi)
  {
    return tableHead(multi, false);
  }

  /** @param compact narrower variant for the home page side by side layout */
  public static String tableHead(boolean multi, boolean compact)
  {
    StringBuilder sb = new StringBuilder();
    sb.append("<thead><tr>");
    sb.append("<th>Shard</th><th class='num'>Height</th><th>Age</th><th class='num'>Txs</th>");
    if (!compact) sb.append("<th class='num'>Size</th>");
    sb.append("<th class='num'>Reward</th>");
    if (multi) sb.append("<th class='num' title='Blocks imported from other shards'>Imports</th>");
    sb.append("<th>Miner</th><th>Hash</th></tr></thead>");
    return sb.toString();
  }

  public static String row(BlockInfo bi, boolean multi)
  {
    return row(bi, multi, false);
  }

  public static String row(BlockInfo bi, boolean multi, boolean compact)
  {
    StringBuilder sb = new StringBuilder();
    sb.append("<tr>");
    sb.append("<td>").append(Html.shardBadge(bi.shard)).append("</td>");
    sb.append("<td class='num'>").append(Html.blockHeightLink(bi.shard, bi.height)).append("</td>");
    sb.append("<td>").append(Html.time(bi.timestamp)).append("</td>");
    sb.append("<td class='num'>").append(bi.tx_count).append("</td>");
    if (!compact) sb.append("<td class='num'>").append(Html.bytes(bi.size)).append("</td>");
    sb.append("<td class='num'>").append(Html.amountShort(bi.coinbase_total)).append("</td>");
    if (multi) sb.append("<td class='num'>").append(bi.import_count).append("</td>");
    sb.append("<td class='ell").append(compact ? " tight" : "").append("' title='").append(Html.esc(bi.remark)).append(bi.remark.length() > 0 ? " · " : "").append(Html.esc(bi.miner_addr)).append("'>");
    if (bi.remark.length() > 0)
    {
      sb.append("<b>").append(Html.esc(bi.remark)).append("</b> ");
    }
    sb.append("<span class='small'>").append(Html.addrLink(bi.miner_addr)).append("</span></td>");
    sb.append("<td>").append(Html.blockLink(bi.hash)).append("</td>");
    sb.append("</tr>\n");
    return sb.toString();
  }

  /** Cursor for paging across shards: shard -> next height to consider */
  public static java.util.TreeMap<Integer, Integer> parseCursor(Shackleton sh, String cursor)
  {
    java.util.TreeMap<Integer, Integer> c = new java.util.TreeMap<>();
    if (cursor != null)
    {
      for (String part : cursor.split(","))
      {
        String[] kv = part.trim().split(":");
        if (kv.length != 2) continue;
        try
        {
          c.put(Integer.parseInt(kv[0]), Integer.parseInt(kv[1]));
        }
        catch (NumberFormatException e)
        {
        }
      }
    }
    if (c.size() == 0)
    {
      for (ShardStats ss : sh.getIndex().getShardStats().values()) c.put(ss.shard, ss.height);
    }
    return c;
  }

  public static String cursorString(Map<Integer, Integer> c)
  {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<Integer, Integer> me : c.entrySet())
    {
      if (sb.length() > 0) sb.append(',');
      sb.append(me.getKey()).append(':').append(me.getValue());
    }
    return sb.toString();
  }

  /** Block at a height of a shard, from the index if possible */
  private static BlockInfo blockAt(Shackleton sh, int shard, int height)
  {
    BlockInfo bi = findInIndex(sh.getIndex(), shard, height);
    if (bi == null) bi = sh.getCache().getBlockInfoAtHeight(shard, height);
    return bi;
  }

  /**
   * Next blocks across all shards ordered by time (newest first), starting at the cursor.
   * The cursor is advanced in place; shards that run out are replaced by their parent shard.
   */
  public static List<BlockInfo> mergedRows(Shackleton sh, java.util.TreeMap<Integer, Integer> cursor, int count)
  {
    ChainIndex index = sh.getIndex();
    ArrayList<BlockInfo> rows = new ArrayList<>();
    java.util.HashMap<Integer, BlockInfo> heads = new java.util.HashMap<>();
    int guard = 0;
    while ((rows.size() < count) && (guard++ < count * 4))
    {
      // make sure we have the candidate block for every shard in the cursor
      for (Integer shard : new ArrayList<>(cursor.keySet()))
      {
        if (heads.containsKey(shard)) continue;
        int h = cursor.get(shard);
        BlockInfo bi = (h >= 0) ? blockAt(sh, shard, h) : null;
        if ((bi == null) || (bi.shard != shard))
        {
          // this shard has no block at that height: continue with the parent shard, if known
          cursor.remove(shard);
          ChainIndex.ShardTree tree = index.getShardTree();
          if ((shard > 0) && (tree != null) && (tree.nodes.get(shard) != null) && (tree.nodes.get(shard).first_height >= 0) && (h < tree.nodes.get(shard).first_height || bi == null))
          {
            int parent = ShardUtil.getShardParentId(shard);
            int ph = Math.min(h, tree.nodes.get(shard).first_height - 1);
            Integer existing = cursor.get(parent);
            if ((existing == null) || (existing > ph)) cursor.put(parent, ph);
            heads.remove(parent);
          }
          continue;
        }
        heads.put(shard, bi);
      }
      if (heads.size() == 0) break;
      // pick the newest candidate
      BlockInfo best = null;
      for (BlockInfo bi : heads.values())
      {
        if ((best == null) || (bi.timestamp > best.timestamp) || ((bi.timestamp == best.timestamp) && (bi.height > best.height))) best = bi;
      }
      rows.add(best);
      heads.remove(best.shard);
      cursor.put(best.shard, best.height - 1);
      if (best.height - 1 < 0) cursor.remove(best.shard);
    }
    return rows;
  }

  /** /blocks - overview over all shards, with infinite scroll */
  public static String renderAll(Shackleton sh, Map<String, String> q, boolean fragment)
    throws Exception
  {
    ChainIndex index = sh.getIndex();
    Map<Integer, ShardStats> stats = index.getShardStats();
    boolean multi = stats.size() > 1;
    boolean compact = q.containsKey("compact");
    java.util.TreeMap<Integer, Integer> cursor = parseCursor(sh, q.get("cursor"));
    List<BlockInfo> rows = mergedRows(sh, cursor, PAGE_SIZE);
    String next_url = (cursor.size() > 0) ? "/blocks?cursor=" + cursorString(cursor) + "&fragment=1" + (compact ? "&compact=1" : "") : null;

    StringBuilder sb = new StringBuilder();
    if (fragment)
    {
      for (BlockInfo bi : rows) sb.append(row(bi, multi, compact));
      if ((next_url != null) && (rows.size() > 0)) sb.append("<tr data-next='").append(next_url).append("' style='display:none'></tr>");
      return sb.toString();
    }

    sb.append("<div class='panel'><h1>Blocks</h1>");
    sb.append("<div class='toolbar'><span class='muted small'>Latest blocks across all shards, newest first.</span><span class='sp'></span>");
    sb.append("<a class='btn' href='/blocks'>All shards</a>");
    for (ShardStats ss : stats.values())
    {
      sb.append("<a class='btn' href='/blocks?shard=").append(ss.shard).append("' style='border-color:").append(Html.shardColor(ss.shard)).append("'>Shard ").append(ss.shard).append("</a>");
    }
    sb.append("</div>");
    sb.append("<div class='tblwrap'><table class='tbl' id='blocktable'>");
    sb.append(tableHead(multi));
    sb.append("<tbody>");
    for (BlockInfo bi : rows) sb.append(row(bi, multi));
    sb.append("</tbody></table></div>");
    sb.append("<div class='pager'>");
    if (next_url != null) sb.append("<button class='btn primary' id='loadmore' data-url='").append(next_url).append("'>Load more</button>");
    sb.append("</div></div>");
    return sb.toString();
  }

  /** /blocks?shard=S&from=H */
  public static String renderShard(Shackleton sh, int shard, Map<String, String> q, boolean fragment)
    throws Exception
  {
    ChainCache cache = sh.getCache();
    ChainIndex index = sh.getIndex();
    boolean multi = index.getShardStats().size() > 1;

    int head = index.getHeightForConfirmations(shard);
    if (head < 0) head = cache.getShardHeight(shard);
    if (head < 0)
    {
      throw new WebUtil.NotFoundException("Shard " + shard + " is not known to this node.");
    }
    int from = WebUtil.intParam(q, "from", head);
    from = WebUtil.clamp(from, 0, head);
    int first_height = 0;
    ChainIndex.ShardTree tree = index.getShardTree();
    if ((tree != null) && tree.nodes.containsKey(shard) && (tree.nodes.get(shard).first_height >= 0))
    {
      first_height = tree.nodes.get(shard).first_height;
    }

    List<BlockInfo> rows = new ArrayList<>();
    int h = from;
    int stop = Math.max(first_height, from - PAGE_SIZE + 1);
    while (h >= stop)
    {
      BlockInfo bi = findInIndex(index, shard, h);
      if (bi == null) bi = cache.getBlockInfoAtHeight(shard, h);
      if (bi == null) break;
      if (bi.shard != shard) break;
      rows.add(bi);
      h--;
    }
    int next_from = h;
    boolean has_next = (rows.size() > 0) && (next_from >= first_height) && (next_from >= 0) && (rows.size() == (from - stop + 1));
    String next_url = "/blocks?shard=" + shard + "&from=" + next_from;

    StringBuilder sb = new StringBuilder();
    if (fragment)
    {
      for (BlockInfo bi : rows) sb.append(row(bi, multi));
      if (has_next) sb.append("<tr data-next='").append(next_url).append("&fragment=1' style='display:none'></tr>");
      return sb.toString();
    }

    sb.append("<div class='panel'><h1>Blocks ").append(Html.shardBadge(shard)).append("</h1>");
    sb.append("<div class='toolbar'><span class='muted small'>Blocks of shard ").append(shard).append(" from height ").append(Html.num(from)).append(" downwards.</span><span class='sp'></span>");
    sb.append("<a class='btn' href='/blocks'>All shards</a><a class='btn' href='/shard/").append(shard).append("'>Shard details</a>");
    if (from < head) sb.append("<a class='btn' href='/blocks?shard=").append(shard).append("'>Latest</a>");
    sb.append("</div>");
    sb.append("<div class='tblwrap'><table class='tbl' id='blocktable'>");
    sb.append(tableHead(multi));
    sb.append("<tbody>");
    for (BlockInfo bi : rows) sb.append(row(bi, multi));
    sb.append("</tbody></table></div>");
    sb.append("<div class='pager'>");
    if (from < head)
    {
      int newer = Math.min(head, from + PAGE_SIZE);
      sb.append("<a class='btn' href='/blocks?shard=").append(shard).append("&from=").append(newer).append("'>&larr; Newer</a>");
    }
    if (has_next)
    {
      sb.append("<button class='btn primary' id='loadmore' data-url='").append(next_url).append("&fragment=1'>Load more</button>");
      sb.append("<a class='btn' href='").append(next_url).append("'>Older &rarr;</a>");
    }
    sb.append("</div></div>");
    return sb.toString();
  }

  private static BlockInfo findInIndex(ChainIndex index, int shard, int height)
  {
    List<BlockInfo> lst = index.getRecent(shard);
    if (lst.size() == 0) return null;
    int top = lst.get(0).height;
    int idx = top - height;
    if ((idx < 0) || (idx >= lst.size())) return null;
    BlockInfo bi = lst.get(idx);
    if (bi.height == height) return bi;
    return null;
  }

  /** Page that asks which shard a height refers to */
  public static String renderChooseShard(Shackleton sh, int height)
  {
    ChainIndex index = sh.getIndex();
    StringBuilder sb = new StringBuilder();
    sb.append("<div class='panel'><h1>Block height ").append(Html.num(height)).append("</h1>");
    sb.append("<p>The network has multiple shards, a height alone is ambiguous. Pick the shard:</p><ul>");
    int n = 0;
    ChainIndex.ShardTree tree = index.getShardTree();
    if (tree != null)
    {
      for (ChainIndex.ShardNode node : tree.nodes.values())
      {
        if ((node.first_height >= 0) && (node.first_height <= height) && (node.last_height >= height))
        {
          sb.append("<li>").append(Html.shardBadge(node.id)).append(" <a href='/block/").append(node.id).append("/").append(height).append("'>block ").append(Html.num(height)).append(" in shard ").append(node.id).append("</a>");
          if (!node.active) sb.append(" <span class='muted small'>(shard split later)</span>");
          sb.append("</li>");
          n++;
        }
      }
    }
    if (n == 0)
    {
      for (ShardStats ss : index.getShardStats().values())
      {
        sb.append("<li>").append(Html.shardBadge(ss.shard)).append(" <a href='/block/").append(ss.shard).append("/").append(height).append("'>shard ").append(ss.shard).append("</a></li>");
      }
    }
    sb.append("</ul></div>");
    return sb.toString();
  }
}
