package snowblossom.shackleton;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.ChainHash;
import snowblossom.lib.Globals;
import snowblossom.lib.NetworkParams;

/**
 * HTML rendering helpers: page chrome, formatting, links.
 * Everything is self-contained (no external CSS/JS dependencies).
 */
public class Html
{
  private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.US);
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter DATE_SHORT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

  public static final String[] NAV = new String[]{
    "/", "Home",
    "/blocks", "Blocks",
    "/shards", "Shards",
    "/richlist", "Rich List",
    "/mining", "Mining",
    "/stats", "Stats",
    "/peers", "Peers",
    "/api", "API",
  };

  // ---------------------------------------------------------------
  // Escaping
  // ---------------------------------------------------------------
  public static String esc(String s)
  {
    if (s == null) return "";
    StringBuilder sb = new StringBuilder(s.length() + 16);
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      switch (c)
      {
        case '&': sb.append("&amp;"); break;
        case '<': sb.append("&lt;"); break;
        case '>': sb.append("&gt;"); break;
        case '"': sb.append("&quot;"); break;
        case '\'': sb.append("&#39;"); break;
        default: sb.append(c);
      }
    }
    return sb.toString();
  }

  /** Escape for use inside a URL path/query component */
  public static String url(String s)
  {
    try
    {
      return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }
    catch (java.io.UnsupportedEncodingException e)
    {
      throw new RuntimeException(e);
    }
  }

  // ---------------------------------------------------------------
  // Number / amount formatting
  // ---------------------------------------------------------------
  public static String num(long n)
  {
    return new DecimalFormat("#,##0", SYMBOLS).format(n);
  }

  public static String dec(double d, int places)
  {
    if (Double.isNaN(d) || Double.isInfinite(d)) return "-";
    StringBuilder p = new StringBuilder("#,##0");
    if (places > 0)
    {
      p.append('.');
      for (int i = 0; i < places; i++) p.append('0');
    }
    return new DecimalFormat(p.toString(), SYMBOLS).format(d);
  }

  /** Exact formatting of flakes as SNOW with 6 decimals */
  public static String snow(long flakes)
  {
    BigDecimal bd = new BigDecimal(flakes).movePointLeft(6);
    return new DecimalFormat("#,##0.000000", SYMBOLS).format(bd);
  }

  /** Compact SNOW amount (2 decimals) for dense tables */
  public static String snowShort(long flakes)
  {
    BigDecimal bd = new BigDecimal(flakes).movePointLeft(6);
    return new DecimalFormat("#,##0.00", SYMBOLS).format(bd);
  }

  public static String amount(long flakes)
  {
    return "<span class='amt' title='" + snow(flakes) + " SNOW'>" + snow(flakes) + "</span>";
  }

  public static String amountShort(long flakes)
  {
    return "<span class='amt' title='" + snow(flakes) + " SNOW'>" + snowShort(flakes) + "</span>";
  }

  public static String bytes(long b)
  {
    if (b < 1024) return b + " B";
    if (b < 1024L * 1024L) return dec(b / 1024.0, 1) + " kB";
    if (b < 1024L * 1024L * 1024L) return dec(b / (1024.0 * 1024.0), 2) + " MB";
    return dec(b / (1024.0 * 1024.0 * 1024.0), 2) + " GB";
  }

  public static String hashrate(double hps)
  {
    if (Double.isNaN(hps) || Double.isInfinite(hps) || hps <= 0) return "-";
    String[] units = {"H/s", "kH/s", "MH/s", "GH/s", "TH/s", "PH/s", "EH/s"};
    int u = 0;
    while ((hps >= 1000.0) && (u < units.length - 1))
    {
      hps /= 1000.0;
      u++;
    }
    return dec(hps, (hps < 10) ? 2 : 1) + " " + units[u];
  }

  /** "2.5×", "220×", "1,500×" */
  public static String factor(double f)
  {
    if (Double.isNaN(f) || Double.isInfinite(f) || f <= 0) return "-";
    if (f < 10) return dec(f, 2) + "×";
    if (f < 100) return dec(f, 1) + "×";
    return dec(f, 0) + "×";
  }

  /** Snow field progress as hashrate: how much of the hashrate needed for the next field the network has */
  public static String fieldProgress(snowblossom.shackleton.ChainIndex.NetStats ns, boolean verbose)
  {
    if (ns.next_field_name == null) return "highest field reached";
    StringBuilder sb = new StringBuilder();
    sb.append(bar(ns.field_hashrate_ratio, "")).append(" ").append(pct(ns.field_hashrate_ratio)).append(" of the hashrate needed");
    if (verbose)
    {
      sb.append(" (").append(factor(ns.field_hashrate_factor)).append(" more) · difficulty ").append(dec(ns.avg_diff, 2)).append(" of ").append(dec(ns.next_field_diff, 0));
    }
    return sb.toString();
  }

  /** Longer explanation of the snow field situation */
  public static String fieldExplanation(snowblossom.shackleton.ChainIndex.NetStats ns)
  {
    if (ns.next_field_name == null) return "The highest snow field is active.";
    StringBuilder sb = new StringBuilder();
    sb.append("The next field, <b>").append(esc(ns.next_field_name)).append("</b>, activates at difficulty ").append(dec(ns.next_field_diff, 0));
    sb.append("; the average difficulty is ").append(dec(ns.avg_diff, 2)).append(", so the network has ").append(pct(ns.field_hashrate_ratio))
      .append(" of the required hashrate and would need ").append(factor(ns.field_hashrate_factor)).append(" as much. ");
    if (ns.avg_diff < ns.field_activation_diff)
    {
      sb.append("The difficulty is currently below the activation difficulty of the active field ").append(esc(ns.field_name)).append(" (").append(dec(ns.field_activation_diff, 0)).append("); fields never deactivate.");
    }
    else
    {
      sb.append("Relative to ").append(esc(ns.field_name)).append("'s activation (").append(dec(ns.field_activation_diff, 0)).append(") the difficulty is ").append(pct(ns.field_progress)).append(" of the way to the next activation.");
    }
    return sb.toString();
  }

  public static String pct(double ratio)
  {
    return dec(ratio * 100.0, 2) + "%";
  }

  // ---------------------------------------------------------------
  // Time formatting
  // ---------------------------------------------------------------
  public static String date(long ms)
  {
    return DATE_FMT.format(Instant.ofEpochMilli(ms)) + " UTC";
  }

  public static String dateShort(long ms)
  {
    return DATE_SHORT_FMT.format(Instant.ofEpochMilli(ms));
  }

  public static String day(long ms)
  {
    return DAY_FMT.format(Instant.ofEpochMilli(ms));
  }

  public static String duration(long ms)
  {
    if (ms < 0) ms = 0;
    long s = ms / 1000L;
    if (s < 60) return s + " s";
    long m = s / 60;
    if (m < 60) return m + " min";
    long h = m / 60;
    m = m % 60;
    if (h < 48) return h + " h " + m + " min";
    long d = h / 24;
    h = h % 24;
    return d + " d " + h + " h";
  }

  public static String ago(long ts)
  {
    long delta = System.currentTimeMillis() - ts;
    if (delta < 0) return "in " + duration(-delta);
    return duration(delta) + " ago";
  }

  /** A time cell that shows relative time and the absolute time on hover */
  public static String time(long ts)
  {
    return "<time data-ts='" + ts + "' title='" + date(ts) + "'>" + ago(ts) + "</time>";
  }

  /** Relative time plus absolute time, for detail pages */
  public static String timeFull(long ts)
  {
    return date(ts) + " <span class='muted'>(<time data-ts='" + ts + "'>" + ago(ts) + "</time>)</span>";
  }

  // ---------------------------------------------------------------
  // Links and badges
  // ---------------------------------------------------------------
  public static String shortHash(String h)
  {
    if (h == null) return "";
    if (h.length() <= 20) return h;
    return h.substring(0, 10) + "…" + h.substring(h.length() - 8);
  }

  public static String shortAddr(String a)
  {
    if (a == null) return "";
    if (a.length() <= 22) return a;
    int colon = a.indexOf(':');
    String prefix = "";
    String rest = a;
    if (colon > 0)
    {
      prefix = a.substring(0, colon + 1);
      rest = a.substring(colon + 1);
    }
    if (rest.length() <= 14) return a;
    return prefix + rest.substring(0, 6) + "…" + rest.substring(rest.length() - 6);
  }

  public static String shardColor(int shard)
  {
    int hue = (shard * 47 + 210) % 360;
    return "hsl(" + hue + ",62%,44%)";
  }

  public static String shardBadge(int shard)
  {
    return "<a class='shard' href='/shard/" + shard + "' style='--sc:" + shardColor(shard) + "' title='Shard " + shard + "'>S" + shard + "</a>";
  }

  public static String blockLink(ChainHash hash)
  {
    String h = hash.toString();
    return "<a class='mono' href='/block/" + h + "' title='" + h + "'>" + shortHash(h) + "</a>";
  }

  public static String blockLinkFull(ChainHash hash)
  {
    String h = hash.toString();
    return "<a class='mono hash' href='/block/" + h + "'>" + h + "</a>";
  }

  public static String blockHeightLink(int shard, int height)
  {
    return "<a href='/block/" + shard + "/" + height + "'>" + num(height) + "</a>";
  }

  public static String txLink(ChainHash hash)
  {
    String h = hash.toString();
    return "<a class='mono' href='/tx/" + h + "' title='" + h + "'>" + shortHash(h) + "</a>";
  }

  public static String txLinkFull(ChainHash hash)
  {
    String h = hash.toString();
    return "<a class='mono hash' href='/tx/" + h + "'>" + h + "</a>";
  }

  public static String addrLink(String addr)
  {
    return "<a class='mono' href='/address/" + esc(addr) + "' title='" + esc(addr) + "'>" + esc(shortAddr(addr)) + "</a>";
  }

  public static String addrLinkFull(String addr)
  {
    return "<a class='mono hash' href='/address/" + esc(addr) + "'>" + esc(addr) + "</a>";
  }

  public static String addrLink(AddressSpecHash hash, NetworkParams params)
  {
    return addrLink(AddressUtil.getAddressString(params.getAddressPrefix(), hash));
  }

  public static String addrLinkFull(AddressSpecHash hash, NetworkParams params)
  {
    return addrLinkFull(AddressUtil.getAddressString(params.getAddressPrefix(), hash));
  }

  public static String copy(String value)
  {
    return "<button class='copy' type='button' data-copy='" + esc(value) + "' title='Copy to clipboard' aria-label='Copy'>" +
      "<svg viewBox='0 0 24 24' width='14' height='14' fill='none' stroke='currentColor' stroke-width='2'><rect x='9' y='9' width='11' height='11' rx='2'/><path d='M5 15V5a2 2 0 0 1 2-2h10'/></svg></button>";
  }

  public static String pill(String cls, String text)
  {
    return "<span class='pill " + cls + "'>" + text + "</span>";
  }

  /** Horizontal bar, ratio between 0 and 1 */
  public static String bar(double ratio, String cls)
  {
    int p = (int) Math.round(Math.max(0, Math.min(1, ratio)) * 100);
    return "<span class='bar " + cls + "' title='" + p + "%'><span style='width:" + p + "%'></span></span>";
  }

  public static String kv(String key, String value)
  {
    return "<dt>" + key + "</dt><dd>" + value + "</dd>\n";
  }

  public static String card(String key, String value, String sub)
  {
    StringBuilder sb = new StringBuilder();
    sb.append("<div class='card'><div class='k'>").append(key).append("</div><div class='v'>").append(value).append("</div>");
    if (sub != null) sb.append("<div class='s'>").append(sub).append("</div>");
    sb.append("</div>\n");
    return sb.toString();
  }

  public static String cardLink(String href, String key, String value, String sub)
  {
    StringBuilder sb = new StringBuilder();
    sb.append("<a class='card' href='").append(href).append("'><div class='k'>").append(key).append("</div><div class='v'>").append(value).append("</div>");
    if (sub != null) sb.append("<div class='s'>").append(sub).append("</div>");
    sb.append("</a>\n");
    return sb.toString();
  }

  public static String networkLabel(NetworkParams params)
  {
    String n = params.getNetworkName();
    if (n.equals("snowblossom")) return "mainnet";
    if (n.equals("teapot")) return "testnet";
    if (n.equals("spoon")) return "regtest";
    return n;
  }

  public static String pager(String base_url, int page, boolean has_next)
  {
    StringBuilder sb = new StringBuilder();
    sb.append("<div class='pager'>");
    if (page > 1)
    {
      sb.append("<a class='btn' href='").append(base_url).append("page=").append(page - 1).append("'>&larr; Newer</a>");
    }
    sb.append("<span class='muted'>Page ").append(page).append("</span>");
    if (has_next)
    {
      sb.append("<a class='btn' href='").append(base_url).append("page=").append(page + 1).append("'>Older &rarr;</a>");
    }
    sb.append("</div>");
    return sb.toString();
  }

  // ---------------------------------------------------------------
  // Page chrome
  // ---------------------------------------------------------------
  public static final String LOGO_SVG =
    "<svg class='logo' viewBox='0 0 24 24' width='22' height='22' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true'>" +
    "<path d='M12 2v20M2 12h20M4.93 4.93l14.14 14.14M19.07 4.93L4.93 19.07'/>" +
    "<path d='M12 2l-2.2 2.2M12 2l2.2 2.2M12 22l-2.2-2.2M12 22l2.2-2.2M2 12l2.2-2.2M2 12l2.2 2.2M22 12l-2.2-2.2M22 12l-2.2 2.2'/>" +
    "</svg>";

  public static String page(Shackleton sh, String title, String active_path, String body)
  {
    NetworkParams params = sh.getParams();
    String net = networkLabel(params);
    StringBuilder sb = new StringBuilder(body.length() + 12000);
    sb.append("<!DOCTYPE html>\n<html lang='en'>\n<head>\n<meta charset='utf-8'>\n");
    sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>\n");
    sb.append("<title>").append(esc(title)).append(" · Snowblossom Explorer</title>\n");
    sb.append("<link rel='icon' href='/static/favicon.svg' type='image/svg+xml'>\n");
    sb.append("<meta name='description' content='Snowblossom ").append(net).append(" block explorer: blocks, transactions, addresses, shards, mining statistics.'>\n");
    sb.append("<script>try{var t=localStorage.getItem('theme');if(t)document.documentElement.setAttribute('data-theme',t)}catch(e){}</script>\n");
    sb.append("<style>").append(CSS).append("</style>\n");
    sb.append("</head>\n<body>\n");

    sb.append("<header class='top'><div class='wrap hdr'>\n");
    sb.append("<a class='brand' href='/'>").append(LOGO_SVG).append("<span>Snowblossom</span><span class='net ").append(net).append("'>").append(net).append("</span></a>\n");
    sb.append("<button class='navtoggle' id='navtoggle' aria-label='Menu'>&#9776;</button>\n");
    sb.append("<nav id='nav'>");
    for (int i = 0; i < NAV.length; i += 2)
    {
      String href = NAV[i];
      String label = NAV[i + 1];
      boolean act = href.equals(active_path);
      sb.append("<a href='").append(href).append("'").append(act ? " class='active'" : "").append(">").append(label).append("</a>");
    }
    sb.append("</nav>\n");
    sb.append("<form class='search' action='/search' method='get' role='search'>");
    sb.append("<input type='search' name='q' placeholder='Block, tx, address, height, s1:1234, name…' aria-label='Search' autocomplete='off'>");
    sb.append("<button type='submit' aria-label='Search'><svg viewBox='0 0 24 24' width='16' height='16' fill='none' stroke='currentColor' stroke-width='2.2'><circle cx='11' cy='11' r='7'/><path d='M20 20l-3.5-3.5'/></svg></button>");
    sb.append("</form>\n");
    sb.append("<button class='theme' id='themebtn' title='Toggle dark / light mode' aria-label='Toggle theme'>");
    sb.append("<svg viewBox='0 0 24 24' width='18' height='18' fill='none' stroke='currentColor' stroke-width='2'><circle cx='12' cy='12' r='9'/><path d='M12 3a9 9 0 0 0 0 18z' fill='currentColor'/></svg></button>\n");
    sb.append("</div></header>\n");

    sb.append("<main class='wrap'>\n").append(body).append("\n</main>\n");

    sb.append("<footer class='wrap'><div>");
    sb.append("<span>Snowblossom Explorer (Shackleton) · ").append(esc(net)).append("</span>");
    sb.append("<span><a href='https://snowblossom.org'>snowblossom.org</a> · <a href='https://github.com/snowblossomcoin/snowblossom'>source</a> · <a href='/api'>API</a></span>");
    sb.append("</div></footer>\n");
    sb.append("<script>").append(JS).append("</script>\n");
    sb.append("</body>\n</html>\n");
    return sb.toString();
  }

  public static String errorBody(String title, String msg)
  {
    return "<div class='panel error'><h2>" + esc(title) + "</h2><p>" + esc(msg) + "</p><p><a class='btn' href='/'>Back to home</a></p></div>";
  }

  public static final String CSS =
    ":root{--bg:#f3f5fa;--bg2:#ffffff;--bg3:#e9edf5;--fg:#1b2230;--muted:#5f6c84;--line:#dbe1ec;--accent:#2e6bea;--accent-fg:#ffffff;--ok:#15803d;--warn:#b45309;--bad:#b91c1c;--hover:#f0f4fb;--mono:ui-monospace,SFMono-Regular,Menlo,Consolas,'Liberation Mono',monospace;--sans:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;--shadow:0 1px 2px rgba(16,24,40,.06),0 1px 3px rgba(16,24,40,.08)}\n" +
    "@media(prefers-color-scheme:dark){:root:not([data-theme=light]){--bg:#0f1419;--bg2:#171d26;--bg3:#202835;--fg:#e4e9f1;--muted:#96a2b8;--line:#2a3342;--accent:#6b9cff;--accent-fg:#0b1220;--ok:#34d399;--warn:#fbbf24;--bad:#f87171;--hover:#1c2430;--shadow:none}}\n" +
    ":root[data-theme=dark]{--bg:#0f1419;--bg2:#171d26;--bg3:#202835;--fg:#e4e9f1;--muted:#96a2b8;--line:#2a3342;--accent:#6b9cff;--accent-fg:#0b1220;--ok:#34d399;--warn:#fbbf24;--bad:#f87171;--hover:#1c2430;--shadow:none}\n" +
    "*{box-sizing:border-box}html{-webkit-text-size-adjust:100%}body{margin:0;background:var(--bg);color:var(--fg);font-family:var(--sans);font-size:15px;line-height:1.45}\n" +
    "a{color:var(--accent);text-decoration:none}a:hover{text-decoration:underline}h1,h2,h3{margin:0 0 .5em;font-weight:650;letter-spacing:-.01em}h1{font-size:24px}h2{font-size:17px}h3{font-size:15px}\n" +
    ".wrap{max-width:1320px;margin:0 auto;padding:0 16px}main.wrap{padding-top:18px;padding-bottom:40px}\n" +
    "header.top{position:sticky;top:0;z-index:20;background:var(--bg2);border-bottom:1px solid var(--line);box-shadow:var(--shadow)}header .hdr{display:flex;align-items:center;gap:14px;min-height:56px;flex-wrap:wrap;padding-top:6px;padding-bottom:6px}\n" +
    ".brand{display:flex;align-items:center;gap:8px;font-weight:700;font-size:17px;color:var(--fg);white-space:nowrap}.brand:hover{text-decoration:none}.brand .logo{color:var(--accent)}\n" +
    ".net{font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:.06em;padding:2px 7px;border-radius:999px;background:var(--bg3);color:var(--muted)}.net.testnet{background:#fde68a;color:#7c2d12}\n" +
    "nav{display:flex;gap:2px;flex-wrap:wrap}nav a{padding:6px 10px;border-radius:8px;color:var(--muted);font-weight:500;font-size:14px}nav a:hover{background:var(--hover);color:var(--fg);text-decoration:none}nav a.active{color:var(--fg);background:var(--bg3)}\n" +
    ".navtoggle{display:none;background:none;border:1px solid var(--line);border-radius:8px;color:var(--fg);font-size:18px;padding:4px 10px;cursor:pointer}\n" +
    "form.search{display:flex;flex:1;min-width:220px;max-width:520px;margin-left:auto}form.search input{flex:1;min-width:0;padding:8px 12px;border:1px solid var(--line);border-right:0;border-radius:8px 0 0 8px;background:var(--bg);color:var(--fg);font-size:14px;font-family:var(--sans)}form.search input:focus{outline:2px solid var(--accent);outline-offset:-1px}form.search button{padding:0 12px;border:1px solid var(--accent);background:var(--accent);color:var(--accent-fg);border-radius:0 8px 8px 0;cursor:pointer;display:flex;align-items:center}\n" +
    ".theme{background:none;border:1px solid var(--line);border-radius:8px;color:var(--muted);padding:6px;cursor:pointer;display:flex;align-items:center}.theme:hover{color:var(--fg)}\n" +
    "@media(max-width:900px){.navtoggle{display:block;order:3}nav{display:none;width:100%;order:5}nav.open{display:flex}form.search{order:4;max-width:none;width:100%;margin-left:0}.theme{order:2;margin-left:auto}}\n" +
    ".cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(175px,1fr));gap:12px;margin-bottom:18px}.card{display:block;background:var(--bg2);border:1px solid var(--line);border-radius:12px;padding:14px 16px;color:var(--fg);box-shadow:var(--shadow)}a.card:hover{text-decoration:none;border-color:var(--accent)}.card .k{font-size:11.5px;color:var(--muted);text-transform:uppercase;letter-spacing:.06em;font-weight:600}.card .v{font-size:22px;font-weight:650;margin-top:4px;line-height:1.2;word-break:break-word}.card .s{font-size:12.5px;color:var(--muted);margin-top:4px}\n" +
    ".panel{scroll-margin-top:70px;background:var(--bg2);border:1px solid var(--line);border-radius:12px;padding:16px 18px;margin-bottom:18px;box-shadow:var(--shadow)}.panel>h2,.panel>h1{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.panel h2 .more{margin-left:auto;font-size:13px;font-weight:500}.panel.error{border-color:var(--bad)}\n" +
    ".grid2{display:grid;grid-template-columns:1fr 1fr;gap:18px}.grid3{display:grid;grid-template-columns:minmax(0,2fr) minmax(0,1fr);gap:18px}.grid2>*,.grid3>*{min-width:0}@media(max-width:1180px){.grid3{grid-template-columns:1fr}}@media(max-width:900px){.grid2{grid-template-columns:1fr}}\n" +
    ".tblwrap{overflow-x:auto;margin:0 -18px;padding:0 18px}table.tbl{width:100%;border-collapse:collapse;font-size:13.5px}.tbl th{text-align:left;font-size:11.5px;color:var(--muted);text-transform:uppercase;letter-spacing:.05em;font-weight:600;padding:8px 10px;border-bottom:1px solid var(--line);white-space:nowrap}.tbl td{padding:7px 10px;border-bottom:1px solid var(--line);white-space:nowrap;vertical-align:middle}.tbl tbody tr:hover td{background:var(--hover)}.tbl tr:last-child td{border-bottom:0}.tbl .num,.tbl th.num{text-align:right}.tbl .wrap{white-space:normal}.tbl td.ell{max-width:260px;overflow:hidden;text-overflow:ellipsis}.tbl td.ell.tight{max-width:190px}\n" +
    ".mono{font-family:var(--mono);font-size:.95em}.hash{word-break:break-all}.muted{color:var(--muted)}.small{font-size:12.5px}.amt{font-family:var(--mono);font-variant-numeric:tabular-nums}.num{font-variant-numeric:tabular-nums}.right{text-align:right}.nowrap{white-space:nowrap}.center{text-align:center}\n" +
    ".shard{display:inline-block;font-family:var(--mono);font-size:11.5px;font-weight:700;color:#fff;background:var(--sc,#555);padding:1px 7px;border-radius:999px;line-height:1.5;vertical-align:middle}a.shard:hover{text-decoration:none;filter:brightness(1.12)}\n" +
    ".pill{display:inline-block;font-size:11.5px;font-weight:600;padding:2px 8px;border-radius:999px;background:var(--bg3);color:var(--muted);vertical-align:middle}.pill.ok{background:rgba(22,163,74,.14);color:var(--ok)}.pill.warn{background:rgba(217,119,6,.14);color:var(--warn)}.pill.bad{background:rgba(220,38,38,.14);color:var(--bad)}.pill.accent{background:rgba(46,107,234,.14);color:var(--accent)}\n" +
    ".bar{display:inline-block;width:90px;height:8px;background:var(--bg3);border-radius:4px;overflow:hidden;vertical-align:middle}.bar>span{display:block;height:100%;background:var(--accent);border-radius:4px}.bar.ok>span{background:var(--ok)}.bar.warn>span{background:var(--warn)}.bar.wide{width:100%}\n" +
    "dl.kv{display:grid;grid-template-columns:max-content 1fr;gap:7px 20px;margin:0;font-size:14px}dl.kv dt{color:var(--muted);white-space:nowrap}dl.kv dd{margin:0;min-width:0;word-break:break-word}@media(max-width:600px){dl.kv{grid-template-columns:1fr}dl.kv dt{margin-top:6px}}\n" +
    ".copy{background:none;border:0;color:var(--muted);cursor:pointer;padding:2px 4px;vertical-align:middle;border-radius:4px}.copy:hover{color:var(--accent);background:var(--hover)}.copy.done{color:var(--ok)}\n" +
    ".btn{display:inline-block;padding:6px 12px;border:1px solid var(--line);border-radius:8px;background:var(--bg2);color:var(--fg);font-size:13.5px;font-weight:500;cursor:pointer;font-family:var(--sans)}.btn:hover{border-color:var(--accent);text-decoration:none}.btn.primary{background:var(--accent);color:var(--accent-fg);border-color:var(--accent)}.btn:disabled{opacity:.5;cursor:default}\n" +
    ".pager{display:flex;gap:10px;align-items:center;justify-content:center;margin-top:14px}.toolbar{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-bottom:12px}.toolbar .sp{flex:1}\n" +
    ".tx{border:1px solid var(--line);border-radius:10px;padding:12px 14px;margin-bottom:12px;background:var(--bg2)}.tx .head{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:10px;font-size:14px}.tx .head .sp{flex:1}.io{display:grid;grid-template-columns:1fr 28px 1fr;gap:8px;align-items:start}.io .arrow{text-align:center;color:var(--muted);padding-top:6px;font-size:18px}.io .col{min-width:0}.io .row{display:flex;justify-content:space-between;gap:8px;padding:5px 8px;border-radius:6px;background:var(--bg);margin-bottom:4px;font-size:13.5px}.io .row .l{min-width:0;overflow:hidden;text-overflow:ellipsis}.io .row .r{white-space:nowrap}.io .sub{font-size:12px;color:var(--muted);padding:0 8px 4px}@media(max-width:700px){.io{grid-template-columns:1fr}.io .arrow{transform:rotate(90deg)}}\n" +
    ".tx .foot{display:flex;gap:16px;flex-wrap:wrap;margin-top:8px;font-size:13px;color:var(--muted)}.hl{background:rgba(46,107,234,.10)}.neg{color:var(--bad)}.pos{color:var(--ok)}\n" +
    ".chart svg{width:100%;height:auto;display:block}.chart .axis{stroke:var(--line)}.chart text{fill:var(--muted);font-size:11px;font-family:var(--sans)}.legend{display:flex;gap:14px;flex-wrap:wrap;font-size:12.5px;margin-top:6px}.legend span i{display:inline-block;width:10px;height:10px;border-radius:2px;margin-right:5px;vertical-align:middle}\n" +
    ".tree svg{width:100%;height:auto;display:block}.tree text{font-family:var(--mono);font-size:12px;fill:#fff;font-weight:700}.tree .lab{fill:var(--muted);font-family:var(--sans);font-weight:400;font-size:11px}.tree line{stroke:var(--line);stroke-width:2}\n" +
    ".tabs{display:flex;gap:4px;border-bottom:1px solid var(--line);margin-bottom:12px;flex-wrap:wrap}.tabs a{padding:7px 12px;border-radius:8px 8px 0 0;color:var(--muted);font-weight:500;font-size:14px}.tabs a.active{color:var(--fg);border-bottom:2px solid var(--accent)}\n" +
    "pre{background:var(--bg);border:1px solid var(--line);border-radius:8px;padding:12px;overflow-x:auto;font-size:13px;font-family:var(--mono)}code{font-family:var(--mono);background:var(--bg3);padding:1px 5px;border-radius:4px;font-size:.92em}\n" +
    "footer{padding:20px 16px 30px;color:var(--muted);font-size:12.5px}footer>div{display:flex;justify-content:space-between;gap:10px;flex-wrap:wrap;border-top:1px solid var(--line);padding-top:14px}footer a{color:var(--muted)}\n" +
    "input.calc{padding:6px 8px;border:1px solid var(--line);border-radius:6px;background:var(--bg);color:var(--fg);font-family:var(--mono);width:130px;text-align:right}select{padding:6px 8px;border:1px solid var(--line);border-radius:6px;background:var(--bg2);color:var(--fg);font-family:var(--sans)}\n" +
    ".split{display:flex;align-items:center;gap:8px}.title-hash{font-family:var(--mono);font-size:14px;word-break:break-all;display:flex;align-items:center;gap:6px;flex-wrap:wrap}\n" +
    ".notice{padding:10px 14px;border-radius:8px;background:rgba(217,119,6,.12);color:var(--warn);font-size:13.5px;margin-bottom:12px}.notice.info{background:rgba(46,107,234,.10);color:var(--accent)}\n";

  public static final String JS =
    "(function(){\n" +
    "var tb=document.getElementById('themebtn');if(tb){tb.addEventListener('click',function(){var r=document.documentElement;var cur=r.getAttribute('data-theme');var sys=window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches;var dark=cur==='dark'||(cur!=='light'&&sys);var n=dark?'light':'dark';r.setAttribute('data-theme',n);try{localStorage.setItem('theme',n)}catch(e){}});}\n" +
    "var nt=document.getElementById('navtoggle');if(nt){nt.addEventListener('click',function(){document.getElementById('nav').classList.toggle('open')});}\n" +
    "document.addEventListener('click',function(e){var t=e.target.closest?e.target.closest('.copy'):null;if(!t)return;var v=t.getAttribute('data-copy');function ok(){t.classList.add('done');setTimeout(function(){t.classList.remove('done')},1200)}if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(v).then(ok,function(){})}else{var ta=document.createElement('textarea');ta.value=v;document.body.appendChild(ta);ta.select();try{document.execCommand('copy');ok()}catch(x){}document.body.removeChild(ta)}});\n" +
    "function dur(ms){if(ms<0)ms=0;var s=Math.floor(ms/1000);if(s<60)return s+' s';var m=Math.floor(s/60);if(m<60)return m+' min';var h=Math.floor(m/60);m=m%60;if(h<48)return h+' h '+m+' min';var d=Math.floor(h/24);h=h%24;return d+' d '+h+' h'}\n" +
    "function refreshTimes(){var now=Date.now();var els=document.querySelectorAll('time[data-ts]');for(var i=0;i<els.length;i++){var ts=parseInt(els[i].getAttribute('data-ts'));var d=now-ts;els[i].textContent=(d<0)?('in '+dur(-d)):(dur(d)+' ago')}}\n" +
    "setInterval(refreshTimes,10000);\n" +
    "var loadedMore=false;function lmBtn(){return document.getElementById('loadmore')}function loadMore(){var lm=lmBtn();if(!lm||lm.disabled)return;var url=lm.getAttribute('data-url');if(!url)return;lm.disabled=true;lm.textContent='Loading\\u2026';loadedMore=true;fetch(url).then(function(r){return r.text()}).then(function(h){var lm2=lmBtn();var tbody=document.querySelector('#blocktable tbody');var tmp=document.createElement('table');tmp.innerHTML='<tbody>'+h+'</tbody>';var rows=tmp.querySelectorAll('tr');var next=null;for(var i=0;i<rows.length;i++){if(rows[i].hasAttribute('data-next')){next=rows[i].getAttribute('data-next');continue}tbody.appendChild(rows[i])}refreshTimes();if(!lm2)return;if(next){lm2.setAttribute('data-url',next);lm2.disabled=false;lm2.textContent='Load more'}else{lm2.textContent='No more blocks';lm2.removeAttribute('data-url')}}).catch(function(){var lm2=lmBtn();if(lm2){lm2.disabled=false;lm2.textContent='Load more'}})}\n" +
    "document.addEventListener('click',function(e){var t=e.target.closest?e.target.closest('#loadmore'):null;if(t){e.preventDefault();loadMore()}});\n" +
    "window.addEventListener('scroll',function(){var lm=lmBtn();if(!lm||lm.disabled||!lm.getAttribute('data-url'))return;if(window.innerHeight+window.scrollY>document.body.offsetHeight-600){loadMore()}});\n" +
    "var ar=document.querySelector('[data-autorefresh]');if(ar){var url=ar.getAttribute('data-autorefresh');setInterval(function(){if(document.hidden||loadedMore)return;fetch(url).then(function(r){return r.text()}).then(function(h){var tmp=document.createElement('div');tmp.innerHTML=h;var n=tmp.firstElementChild;if(n){ar.innerHTML=n.innerHTML;refreshTimes()}}).catch(function(){})},30000);}\n" +
    "var sel=document.getElementById('calcshard');var hr=document.getElementById('hashrate');function calc(){if(!hr)return;var r=(parseFloat(hr.value)||0)*1000;var opt=sel?sel.options[sel.selectedIndex]:null;var net=opt?parseFloat(opt.getAttribute('data-hashrate')):parseFloat(hr.getAttribute('data-hashrate'));var reward=opt?parseFloat(opt.getAttribute('data-reward')):parseFloat(hr.getAttribute('data-reward'));var target=parseFloat(hr.getAttribute('data-target'))||600;var share=(r<=0||!(net>0))?0:r/(net+r);var hour=reward*(3600/target)*share;document.getElementById('calc0').textContent=(share*100).toFixed(share<0.001?4:2);document.getElementById('calc1').textContent=hour.toFixed(4);document.getElementById('calc2').textContent=(hour*24).toFixed(4);document.getElementById('calc3').textContent=(hour*24*30).toFixed(3)}if(hr){hr.addEventListener('input',calc);if(sel)sel.addEventListener('change',calc)}\n" +
    "})();";
}
