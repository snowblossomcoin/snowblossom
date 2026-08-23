package snowblossom.shackleton;

import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.webserver.DuckWebServer;
import duckutil.webserver.WebContext;
import duckutil.webserver.WebHandler;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minidev.json.JSONObject;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.shackleton.WebUtil.NotFoundException;
import snowblossom.shackleton.WebUtil.RedirectException;

public class WebServer implements WebHandler
{
  private static final Logger logger = Logger.getLogger("snowblossom.shackleton");
  private final Shackleton shackleton;
  private final ApiHandler api;

  private static final Pattern SHARD_HEIGHT = Pattern.compile("^(?:s|shard\\s*)?(\\d+)\\s*(?:[:/h]|\\s+height\\s+|\\s+h\\s*|\\s+)\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);

  public WebServer(Config config, Shackleton shackleton)
    throws Exception
  {
    this.shackleton = shackleton;
    this.api = new ApiHandler(shackleton);

    config.require("port");
    String host = null;
    int port = config.getInt("port");
    int threads = config.getIntWithDefault("web_threads", 32);

    new DuckWebServer(host, port, this, threads);
    logger.info("Explorer web server listening on port " + port);
  }

  @Override
  public void handle(WebContext t) throws Exception
  {
    String path = t.getURI().getPath();
    Map<String, String> q = WebUtil.parseQuery(t.getURI().getRawQuery());
    t.getExchange().getResponseHeaders().add("X-Content-Type-Options", "nosniff");

    try
    {
      if (path.startsWith("/api"))
      {
        if (path.equals("/api") || path.equals("/api/"))
        {
          html(t, "API", "/api", api.docs());
          return;
        }
        if (!api.handle(t, path, q))
        {
          t.setContentType("application/json");
          t.out().println("{\"error\":\"unknown api endpoint\"}");
          t.setHttpCode(404);
        }
        return;
      }
      if (path.startsWith("/static/"))
      {
        staticHandle(t, path.substring("/static/".length()));
        return;
      }
      if (path.equals("/robots.txt"))
      {
        t.setContentType("text/plain");
        t.out().println("User-agent: *\nDisallow: /api/\nDisallow: /search\nDisallow: /fragment/\n");
        t.setHttpCode(200);
        return;
      }
      if (path.startsWith("/fragment/"))
      {
        fragment(t, path, q);
        return;
      }
      route(t, path, q);
    }
    catch (RedirectException r)
    {
      redirect(t, r.location);
    }
    catch (NotFoundException nf)
    {
      t.resetBuffer();
      t.setContentType("text/html; charset=utf-8");
      t.out().print(Html.page(shackleton, "Not found", "", Html.errorBody("Not found", nf.getMessage())));
      t.setHttpCode(404);
    }
    catch (Throwable e)
    {
      logger.log(Level.WARNING, "Error handling " + t.getURI() + ": " + e, e);
      t.resetBuffer();
      if (path.startsWith("/api/"))
      {
        t.setContentType("application/json; charset=utf-8");
        JSONObject o = new JSONObject();
        o.put("error", e.toString());
        t.out().println(o.toJSONString());
        t.setHttpCode(500);
        return;
      }
      t.setContentType("text/html; charset=utf-8");
      String msg = e.toString();
      if (msg.contains("UNAVAILABLE")) msg = "The explorer cannot reach its Snowblossom node right now. Please try again in a moment. (" + msg + ")";
      t.out().print(Html.page(shackleton, "Error", "", Html.errorBody("Something went wrong", msg)));
      t.setHttpCode(500);
    }
  }

  private void html(WebContext t, String title, String nav, String body)
  {
    t.setContentType("text/html; charset=utf-8");
    t.getExchange().getResponseHeaders().add("Content-Language", "en");
    t.out().print(Html.page(shackleton, title, nav, body));
    t.setHttpCode(200);
  }

  private void redirect(WebContext t, String location)
  {
    t.resetBuffer();
    t.setContentType("text/html; charset=utf-8");
    t.getExchange().getResponseHeaders().add("Location", location);
    t.out().println("<a href='" + Html.esc(location) + "'>Redirecting</a>");
    t.setHttpCode(302);
  }

  private void fragment(WebContext t, String path, Map<String, String> q)
    throws Exception
  {
    t.setContentType("text/html; charset=utf-8");
    t.getExchange().getResponseHeaders().add("Cache-Control", "no-cache");
    if (path.equals("/fragment/home-blocks"))
    {
      t.out().print("<div>" + HomePage.latestBlocksInner(shackleton) + "</div>");
      t.setHttpCode(200);
      return;
    }
    throw new NotFoundException("Unknown fragment");
  }

  private void route(WebContext t, String path, Map<String, String> q)
    throws Exception
  {
    // strip trailing slash
    if ((path.length() > 1) && path.endsWith("/")) path = path.substring(0, path.length() - 1);

    if (path.equals("") || path.equals("/"))
    {
      if (q.containsKey("search"))
      {
        legacySearch(q.get("search"));
      }
      html(t, "Snowblossom " + Html.networkLabel(shackleton.getParams()) + " explorer", "/", HomePage.render(shackleton));
      return;
    }
    if (path.equals("/search"))
    {
      search(t, q.getOrDefault("q", ""));
      return;
    }
    if (path.equals("/blocks"))
    {
      if (q.containsKey("shard"))
      {
        int shard = WebUtil.intParam(q, "shard", 0);
        boolean fragment = q.containsKey("fragment");
        String body = BlocksPage.renderShard(shackleton, shard, q, fragment);
        if (fragment)
        {
          t.setContentType("text/html; charset=utf-8");
          t.out().print(body);
          t.setHttpCode(200);
        }
        else
        {
          html(t, "Blocks of shard " + shard, "/blocks", body);
        }
        return;
      }
      if (q.containsKey("fragment"))
      {
        t.setContentType("text/html; charset=utf-8");
        t.out().print(BlocksPage.renderAll(shackleton, q, true));
        t.setHttpCode(200);
        return;
      }
      html(t, "Blocks", "/blocks", BlocksPage.renderAll(shackleton, q, false));
      return;
    }
    if (path.startsWith("/block/"))
    {
      String rest = path.substring("/block/".length());
      String[] parts = rest.split("/");
      Block blk = null;
      if ((parts.length == 2) && WebUtil.isDigits(parts[0]) && WebUtil.isDigits(parts[1]))
      {
        int shard = Integer.parseInt(parts[0]);
        int height = Integer.parseInt(parts[1]);
        blk = shackleton.getCache().getBlockAtHeight(shard, height);
        if (blk == null) throw new NotFoundException("No block at height " + height + " in shard " + shard + ".");
      }
      else if ((parts.length == 1) && WebUtil.isDigits(parts[0]) && (parts[0].length() < 10))
      {
        blockByHeight(t, Integer.parseInt(parts[0]));
        return;
      }
      else if ((parts.length == 1) && (parts[0].length() == Globals.BLOCKCHAIN_HASH_LEN * 2) && WebUtil.isHex(parts[0]))
      {
        blk = shackleton.getCache().getBlock(new ChainHash(parts[0].toLowerCase()));
        if (blk == null)
        {
          // maybe it is a transaction hash
          Transaction tx = shackleton.getCache().getTransaction(new ChainHash(parts[0].toLowerCase()));
          if (tx != null) throw new RedirectException("/tx/" + parts[0].toLowerCase());
          throw new NotFoundException("No block with hash " + parts[0] + " is known to this node.");
        }
      }
      else
      {
        throw new NotFoundException("Invalid block reference.");
      }
      int page = WebUtil.intParam(q, "page", 1);
      BlockHeader h = blk.getHeader();
      html(t, "Block " + h.getBlockHeight() + " (shard " + h.getShardId() + ")", "/blocks", BlockPage.render(shackleton, blk, page));
      return;
    }
    if (path.startsWith("/tx/"))
    {
      String hs = path.substring("/tx/".length()).toLowerCase();
      if ((hs.length() != Globals.BLOCKCHAIN_HASH_LEN * 2) || !WebUtil.isHex(hs)) throw new NotFoundException("Invalid transaction hash.");
      Transaction tx = shackleton.getCache().getTransaction(new ChainHash(hs));
      if (tx == null)
      {
        Block blk = shackleton.getCache().getBlock(new ChainHash(hs));
        if (blk != null) throw new RedirectException("/block/" + hs);
        throw new NotFoundException("Transaction " + hs + " is not known to this node (neither confirmed nor in the mempool).");
      }
      html(t, "Transaction " + Html.shortHash(hs), "/blocks", TxPage.render(shackleton, tx));
      return;
    }
    if (path.startsWith("/address/"))
    {
      String as = path.substring("/address/".length());
      AddressSpecHash addr = parseAddress(shackleton.getParams(), as);
      if (addr == null) throw new NotFoundException("'" + as + "' is not a valid address.");
      int page = WebUtil.intParam(q, "page", 1);
      html(t, "Address " + Html.shortAddr(addr.toAddressString(shackleton.getParams())), "", AddressPage.render(shackleton, addr, page));
      return;
    }
    if (path.equals("/shards"))
    {
      html(t, "Shards", "/shards", ShardsPage.render(shackleton));
      return;
    }
    if (path.startsWith("/shard/"))
    {
      String s = path.substring("/shard/".length());
      if (!WebUtil.isDigits(s) || (s.length() > 9)) throw new NotFoundException("Invalid shard id.");
      int shard = Integer.parseInt(s);
      html(t, "Shard " + shard, "/shards", ShardsPage.renderShard(shackleton, shard, q));
      return;
    }
    if (path.equals("/richlist"))
    {
      html(t, "Rich list", "/richlist", RichListPage.render(shackleton, q));
      return;
    }
    if (path.equals("/mining") || path.equals("/votes") || path.equals("/pools"))
    {
      html(t, "Mining", "/mining", MiningPage.render(shackleton));
      return;
    }
    if (path.equals("/stats"))
    {
      html(t, "Statistics", "/stats", StatsPage.render(shackleton, q));
      return;
    }
    if (path.equals("/peers"))
    {
      html(t, "Peers", "/peers", PeersPage.render(shackleton));
      return;
    }
    if (path.equals("/favicon.ico"))
    {
      throw new RedirectException("/static/favicon.svg");
    }
    throw new NotFoundException("There is no page at " + path + ".");
  }

  private void blockByHeight(WebContext t, int height)
    throws Exception
  {
    ChainIndex index = shackleton.getIndex();
    Map<Integer, ChainIndex.ShardStats> stats = index.getShardStats();
    if (stats.size() <= 1)
    {
      int shard = (stats.size() == 1) ? stats.keySet().iterator().next() : 0;
      throw new RedirectException("/block/" + shard + "/" + height);
    }
    // multiple shards: if the height is only valid in one shard, go there
    ChainIndex.ShardTree tree = index.getShardTree();
    TreeSet<Integer> candidates = new TreeSet<>();
    if (tree != null)
    {
      for (ChainIndex.ShardNode n : tree.nodes.values())
      {
        if ((n.first_height >= 0) && (n.first_height <= height) && (n.last_height >= height)) candidates.add(n.id);
      }
    }
    if (candidates.size() == 1) throw new RedirectException("/block/" + candidates.first() + "/" + height);
    html(t, "Block " + height, "/blocks", BlocksPage.renderChooseShard(shackleton, height));
  }

  /** Old style links: /?search=... */
  private void legacySearch(String search)
    throws Exception
  {
    search = search.trim();
    if (search.length() == 0) return;
    if (search.equals("richlist")) throw new RedirectException("/richlist");
    if (search.startsWith("load-")) throw new RedirectException("/blocks");
    throw new RedirectException("/search?q=" + Html.url(search));
  }

  private void search(WebContext t, String query)
    throws Exception
  {
    NetworkParams params = shackleton.getParams();
    ChainCache cache = shackleton.getCache();
    String s = query.trim();
    if (s.length() == 0) throw new RedirectException("/");
    if (s.length() > 200) throw new NotFoundException("Search term too long.");

    // block height
    if (WebUtil.isDigits(s) && (s.length() < 10))
    {
      blockByHeight(t, Integer.parseInt(s));
      return;
    }
    // shard:height
    Matcher m = SHARD_HEIGHT.matcher(s);
    if (m.matches())
    {
      throw new RedirectException("/block/" + Integer.parseInt(m.group(1)) + "/" + Integer.parseInt(m.group(2)));
    }
    // hash
    String hs = s.toLowerCase();
    if (hs.startsWith("0x")) hs = hs.substring(2);
    if ((hs.length() == Globals.BLOCKCHAIN_HASH_LEN * 2) && WebUtil.isHex(hs))
    {
      ChainHash hash = new ChainHash(hs);
      if (cache.getBlock(hash) != null) throw new RedirectException("/block/" + hs);
      if (cache.getTransaction(hash) != null) throw new RedirectException("/tx/" + hs);
      throw new NotFoundException("No block or transaction with hash " + hs + " is known to this node.");
    }
    // address
    AddressSpecHash addr = parseAddress(params, s);
    if (addr != null) throw new RedirectException("/address/" + addr.toAddressString(params));

    // shard
    if (s.toLowerCase().startsWith("shard") && WebUtil.isDigits(s.substring(5).trim()))
    {
      throw new RedirectException("/shard/" + Integer.parseInt(s.substring(5).trim()));
    }
    if (s.toLowerCase().matches("^s\\d{1,4}$"))
    {
      throw new RedirectException("/shard/" + Integer.parseInt(s.substring(1)));
    }

    // names
    String body = nameSearch(s);
    if (body != null)
    {
      html(t, "Search: " + s, "", body);
      return;
    }
    throw new NotFoundException("Nothing found for '" + s + "'. You can search for block hashes, block heights (optionally as shard:height, e.g. 1:1234), transaction hashes, addresses, usernames and channel names.");
  }

  private String nameSearch(String search)
    throws Exception
  {
    TxOutList lst_chan;
    TxOutList lst_user;
    try
    {
      lst_chan = shackleton.getStub().getIDList(
        RequestNameID.newBuilder().setNameType(RequestNameID.IdType.CHANNELNAME).setName(ByteString.copyFrom(search.getBytes())).build());
      lst_user = shackleton.getStub().getIDList(
        RequestNameID.newBuilder().setNameType(RequestNameID.IdType.USERNAME).setName(ByteString.copyFrom(search.getBytes())).build());
    }
    catch (Throwable t)
    {
      logger.log(Level.FINE, "name lookup failed: " + t);
      return null;
    }
    if (lst_chan.getOutListCount() + lst_user.getOutListCount() == 0) return null;

    StringBuilder sb = new StringBuilder();
    sb.append("<div class='panel'><h1>Identifier: ").append(Html.esc(search)).append("</h1>");
    sb.append("<p class='muted small'>Usernames and channel names are claimed through transaction outputs that carry the name.</p></div>");
    for (int pass = 0; pass < 2; pass++)
    {
      TxOutList lst = (pass == 0) ? lst_chan : lst_user;
      if (lst.getOutListCount() == 0) continue;
      sb.append("<div class='panel'><h2>").append(pass == 0 ? "Channels" : "Users").append(" <span class='muted small'>").append(lst.getOutListCount()).append("</span></h2>");
      HashSet<ChainHash> seen = new HashSet<>();
      for (TxOutPoint op : lst.getOutListList())
      {
        ChainHash txh = new ChainHash(op.getTxHash());
        if (!seen.add(txh)) continue;
        Transaction tx = shackleton.getCache().getTransaction(txh);
        if (tx == null) continue;
        sb.append(TxPage.txCard(shackleton, TxView.of(tx, shackleton.getCache(), true), null, false));
      }
      sb.append("</div>");
    }
    return sb.toString();
  }

  public static AddressSpecHash parseAddress(NetworkParams params, String s)
  {
    if (s == null) return null;
    s = s.trim();
    try
    {
      return AddressUtil.getHashForAddress(params.getAddressPrefix(), s);
    }
    catch (Throwable e)
    {
    }
    if ((s.length() == Globals.ADDRESS_SPEC_HASH_LEN * 2) && WebUtil.isHex(s))
    {
      try
      {
        return new AddressSpecHash(s.toLowerCase());
      }
      catch (Throwable e)
      {
      }
    }
    // maybe the prefix was left out
    if (!s.contains(":"))
    {
      try
      {
        return AddressUtil.getHashForAddress(params.getAddressPrefix(), params.getAddressPrefix() + ":" + s);
      }
      catch (Throwable e)
      {
      }
    }
    return null;
  }

  public void staticHandle(WebContext t, String path)
    throws Exception
  {
    if (path.contains("..") || path.startsWith("/"))
    {
      throw new NotFoundException("No such resource.");
    }
    InputStream in = Shackleton.class.getResourceAsStream("/shackleton/webstatic/" + path);
    if (in == null)
    {
      throw new NotFoundException("No resource: " + path);
    }
    String mime_type = Mimer.guessContentType(path);
    if (path.endsWith(".svg")) mime_type = "image/svg+xml";
    if (mime_type != null) t.setContentType(mime_type);
    t.getExchange().getResponseHeaders().add("Cache-Control", "public, max-age=3600");
    byte[] buff = new byte[8192];
    while (true)
    {
      int r = in.read(buff);
      if (r < 0) break;
      t.out().write(buff, 0, r);
    }
    in.close();
    t.out().flush();
    t.setHttpCode(200);
  }

  /** Legacy graph output for the shard visualization */
  public static JSONObject getNetworkGraph(Shackleton shackleton, int blocks_back)
  {
    ChainCache cache = shackleton.getCache();
    NodeStatus node_status = cache.getNodeStatus();
    Map<Integer, BlockHeader> head_map = cache.getNetShardHeads(node_status);

    HashSet<ChainHash> included_blocks = new HashSet<>();
    LinkedList<BlockHeader> block_list = new LinkedList<>();

    for (int shard : new TreeSet<>(head_map.keySet()))
    {
      int count_in_shard = 0;
      BlockHeader head = head_map.get(shard);
      while ((head != null) && (head.getSnowHash().size() > 0) && (count_in_shard < blocks_back) &&
        (!included_blocks.contains(new ChainHash(head.getSnowHash()))))
      {
        ChainHash hash = new ChainHash(head.getSnowHash());
        included_blocks.add(hash);
        block_list.add(head);
        if (head.getBlockHeight() == 0) head = null;
        else head = cache.getHeader(new ChainHash(head.getPrevBlockHash()));
        count_in_shard++;
      }
    }
    return GraphOutput.getGraph(block_list);
  }
}
