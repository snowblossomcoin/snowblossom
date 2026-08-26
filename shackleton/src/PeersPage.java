package snowblossom.shackleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.shackleton.ChainIndex.NetStats;

public class PeersPage
{
  private static volatile long unavailable_until = 0;

  /**
   * The node's GetPeerList RPC never completes (SnowUserService.getPeerList omits onCompleted())
   * and grpc only flushes a unary response when the call is closed, so a blocking call hangs forever.
   * Use the async stub with a short deadline, and remember a failure for a while so the page stays fast.
   */
  public static List<PeerInfo> getPeers(Shackleton sh)
  {
    if (System.currentTimeMillis() < unavailable_until)
    {
      throw new RuntimeException("peer list not available from this node");
    }
    final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
    final PeerList[] holder = new PeerList[1];
    final Throwable[] err = new Throwable[1];
    sh.getAsyncStub().withDeadlineAfter(4, java.util.concurrent.TimeUnit.SECONDS).getPeerList(
      PeerListRequest.newBuilder().setDesiredResults(1000).build(),
      new io.grpc.stub.StreamObserver<PeerList>()
      {
        public void onNext(PeerList pl) { holder[0] = pl; latch.countDown(); }
        public void onError(Throwable t) { err[0] = t; latch.countDown(); }
        public void onCompleted() { latch.countDown(); }
      });
    try
    {
      latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
    }
    catch (InterruptedException e)
    {
    }
    if (holder[0] == null)
    {
      unavailable_until = System.currentTimeMillis() + 5L * 60L * 1000L;
      throw new RuntimeException("peer list not available from this node" + ((err[0] != null) ? ": " + err[0] : ""));
    }
    ArrayList<PeerInfo> peers = new ArrayList<>(holder[0].getPeersList());
    Collections.sort(peers, new Comparator<PeerInfo>()
    {
      public int compare(PeerInfo a, PeerInfo b)
      {
        if (a.getLastPassed() != b.getLastPassed()) return Long.compare(b.getLastPassed(), a.getLastPassed());
        return a.getHost().compareTo(b.getHost());
      }
    });
    return peers;
  }

  public static String render(Shackleton sh)
  {
    NetStats ns = sh.getIndex().getNetStats();
    StringBuilder sb = new StringBuilder();
    List<PeerInfo> peers;
    try
    {
      peers = getPeers(sh);
    }
    catch (Throwable t)
    {
      StringBuilder nb = new StringBuilder();
      nb.append("<div class='cards'>");
      if (ns != null)
      {
        nb.append(Html.card("Connected peers", Html.num(ns.connected_peers), "of our node"));
        nb.append(Html.card("Estimated nodes", Html.num(ns.estimated_nodes), "unique nodes seen in peer gossip"));
        nb.append(Html.card("Mempool", Html.num(ns.mem_pool_size) + " <span class='muted small'>tx</span>", "waiting for confirmation"));
      }
      nb.append("</div>");
      nb.append("<div class='panel'><h1>Peers</h1><div class='notice'>The peer list is not available: the node did not answer the GetPeerList request within a few seconds. ");
      nb.append("(Node versions up to 2.2.0 never complete this RPC because <code>SnowUserService.getPeerList</code> does not call <code>onCompleted()</code>; the explorer retries every few minutes.)</div>");
      if ((ns != null) && (ns.version_map.size() > 0))
      {
        nb.append("<h2>Versions of connected peers</h2><div class='tblwrap'><table class='tbl'><thead><tr><th>Version</th><th class='num'>Peers</th></tr></thead><tbody>");
        for (Map.Entry<String, Integer> me : ns.version_map.entrySet())
        {
          nb.append("<tr><td class='mono'>").append(Html.esc(HexUtil.getSafeString(me.getKey()))).append("</td><td class='num'>").append(me.getValue()).append("</td></tr>");
        }
        nb.append("</tbody></table></div>");
      }
      nb.append("</div>");
      return nb.toString();
    }

    long now = System.currentTimeMillis();
    int recent = 0;
    int tls = 0;
    TreeMap<String, Integer> versions = new TreeMap<>();
    for (PeerInfo p : peers)
    {
      if (p.getLastPassed() > now - 24L * 3600L * 1000L) recent++;
      if (p.getConnectionType() == PeerInfo.ConnectionType.GRPC_TLS) tls++;
      String v = HexUtil.getSafeString(p.getVersion());
      if (v.length() == 0) v = "unknown";
      versions.put(v, versions.getOrDefault(v, 0) + 1);
    }

    sb.append("<div class='cards'>");
    if (ns != null)
    {
      sb.append(Html.card("Connected peers", Html.num(ns.connected_peers), "of our node"));
      sb.append(Html.card("Estimated nodes", Html.num(ns.estimated_nodes), "unique nodes seen in peer gossip"));
      sb.append(Html.card("Mempool", Html.num(ns.mem_pool_size) + " <span class='muted small'>tx</span>", "waiting for confirmation"));
    }
    sb.append(Html.card("Known peers", Html.num(peers.size()), recent + " reachable in the last 24h"));
    sb.append(Html.card("TLS peers", Html.num(tls), "offering encrypted connections"));
    sb.append("</div>");

    int max_id = sh.getParams().getMaxShardId();
    int leaves = (max_id + 1) / 2 + ((max_id + 1) % 2);
    int active = sh.getIndex().getShardStats().size();
    sb.append("<div class='panel'><h2>Peer list</h2>");
    sb.append("<p class='muted small'>Peers listed as <b>seed</b> are the bootstrap addresses built into the node (DNS seeds of the network parameters); they are not nodes with a version called “seed” – the node simply has not exchanged peer information with them yet, so version, shards and node id are unknown. ");
    sb.append("The <b>shards</b> column is the set of shard ids a peer is configured to track, not the shards that exist: ");
    sb.append("shard ids form a binary tree (0 splits into 1 and 2, those into 3–6, …) and this network allows ids 0–").append(max_id)
      .append(", i.e. ").append(max_id + 1).append(" ids of which at most ").append(leaves).append(" can be active at once. ");
    sb.append("A node without a <code>shards</code> setting tracks every id, shown as “all ").append(max_id + 1).append(" shards”. ");
    sb.append("Currently ").append(active).append(active == 1 ? " shard is" : " shards are").append(" active (see <a href='/shards'>Shards</a>).</p>");
    sb.append("<div class='tblwrap'><table class='tbl'><thead><tr><th>Host</th><th class='num'>Port</th><th>Type</th><th>Version</th><th>Shards</th><th>Last reachable</th><th>Learned</th><th>Node id</th></tr></thead><tbody>");
    for (PeerInfo p : peers)
    {
      sb.append("<tr><td class='mono ell' title='").append(Html.esc(p.getHost())).append("'>").append(Html.esc(p.getHost())).append("</td><td class='num'>").append(p.getPort()).append("</td>");
      sb.append("<td>").append(p.getConnectionType() == PeerInfo.ConnectionType.GRPC_TLS ? Html.pill("ok", "TLS") : Html.pill("", "TCP")).append("</td>");
      String ver = HexUtil.getSafeString(p.getVersion());
      if (ver.equals("seed")) sb.append("<td>").append(Html.pill("", "seed")).append(" <span class='muted small' title='bootstrap entry from the network parameters; the node has not talked to this peer yet, so its version and shards are unknown'>not contacted</span></td>");
      else sb.append("<td class='mono small'>").append(Html.esc(ver)).append("</td>");
      sb.append("<td>");
      if (p.getShardIdSetCount() == 0) sb.append("<span class='muted small'>unknown</span>");
      else if (p.getShardIdSetCount() >= sh.getParams().getMaxShardId() + 1) sb.append("<span class='muted small' title='tracks every possible shard id (0–").append(sh.getParams().getMaxShardId()).append("); the network currently has ").append(active).append(" active shard(s)'>all ").append(p.getShardIdSetCount()).append(" shards</span>");
      else
      {
        int k = 0;
        for (int s : p.getShardIdSetList())
        {
          sb.append(Html.shardBadge(s)).append(" ");
          k++;
          if (k >= 8)
          {
            if (p.getShardIdSetCount() > 8) sb.append("<span class='muted small'>+").append(p.getShardIdSetCount() - 8).append("</span>");
            break;
          }
        }
      }
      sb.append("</td>");
      sb.append("<td>").append(p.getLastPassed() > 0 ? Html.time(p.getLastPassed()) : "<span class='muted'>never</span>").append("</td>");
      sb.append("<td>").append(p.getLearned() > 0 ? Html.time(p.getLearned()) : "").append("</td>");
      String nid = HexUtil.getHexString(p.getNodeId());
      sb.append("<td class='mono small' title='").append(nid).append("'>").append(nid.length() > 12 ? nid.substring(0, 12) + "…" : nid).append("</td>");
      sb.append("</tr>");
    }
    sb.append("</tbody></table></div></div>");

    sb.append("<div class='grid2'><div class='panel'><h2>Versions of known peers</h2><div class='tblwrap'><table class='tbl'><thead><tr><th>Version</th><th class='num'>Peers</th><th>Share</th></tr></thead><tbody>");
    for (Map.Entry<String, Integer> me : versions.entrySet())
    {
      double share = (double) me.getValue() / Math.max(1, peers.size());
      sb.append("<tr><td class='mono'>").append(Html.esc(me.getKey())).append("</td><td class='num'>").append(me.getValue()).append("</td><td>").append(Html.bar(share, "")).append(" <span class='small'>").append(Html.pct(share)).append("</span></td></tr>");
    }
    sb.append("</tbody></table></div></div>");
    if ((ns != null) && (ns.version_map.size() > 0))
    {
      sb.append("<div class='panel'><h2>Versions of connected peers</h2><div class='tblwrap'><table class='tbl'><thead><tr><th>Version</th><th class='num'>Peers</th></tr></thead><tbody>");
      for (Map.Entry<String, Integer> me : ns.version_map.entrySet())
      {
        sb.append("<tr><td class='mono'>").append(Html.esc(HexUtil.getSafeString(me.getKey()))).append("</td><td class='num'>").append(me.getValue()).append("</td></tr>");
      }
      sb.append("</tbody></table></div></div>");
    }
    sb.append("</div>");
    return sb.toString();
  }
}
