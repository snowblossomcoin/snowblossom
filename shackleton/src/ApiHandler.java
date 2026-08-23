package snowblossom.shackleton;

import com.google.protobuf.ByteString;
import duckutil.webserver.WebContext;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.shackleton.ChainIndex.NetStats;
import snowblossom.shackleton.ChainIndex.ShardNode;
import snowblossom.shackleton.ChainIndex.ShardStats;
import snowblossom.shackleton.ChainIndex.ShardTree;
import snowblossom.shackleton.TxLocator.TxLocation;

/**
 * JSON API.  Keeps the endpoints of the previous explorer and adds new ones.
 */
public class ApiHandler
{
  private final Shackleton shackleton;

  public ApiHandler(Shackleton shackleton)
  {
    this.shackleton = shackleton;
  }

  public static final String[][] DOCS = new String[][]{
    {"/api/status", "Node and network status, statistics of every active shard"},
    {"/api/shards", "Shard tree: all known shards, their parents, children, first and last blocks"},
    {"/api/block/{hash}", "Block details (header, reward, imports) and its transactions"},
    {"/api/block/{shard}/{height}", "Same, looked up by shard and height"},
    {"/api/blocks?shard=S&from=H&count=N", "List of recent blocks of a shard (newest first, default 50, max 500)"},
    {"/api/tx/{hash}", "Transaction details, inputs with resolved values, outputs and confirmation status"},
    {"/api/address/{address}", "Balances per shard of an address; add ?history=1&count=100&offset=0 for the transaction list (newest first), ?utxos=1 for the list of unspent outputs (slow for large addresses)"},
    {"/api/richlist?count=N", "Top N addresses by balance (default 100, max 1000) and supply statistics"},
    {"/api/mining", "Pools (coinbase remarks), miner addresses and motion votes over recent blocks"},
    {"/api/peers", "Known peers of the node"},
    {"/api/history?range=30d", "Bucketed statistics per shard (blocks, transactions, hashrate, difficulty); range 24h, 7d, 30d, 90d or 1y, or custom days=N&bucket_hours=H"},
    {"/api/total_coins", "Circulating supply as plain text (legacy)"},
    {"/api/total_coins_json", "Circulating supply as JSON (legacy)"},
    {"/api/health_stats", "Per shard block and transaction counts over 1h / 4h (legacy)"},
    {"/api/recent_json_graph", "Recent blocks of all shards as a graph (legacy, used by the shard visualization)"},
    {"/api/block_info_by_height/{shard}/{height}", "Block summary by shard and height (legacy)"},
  };

  public String docs()
  {
    StringBuilder sb = new StringBuilder();
    sb.append("<div class='panel'><h1>API</h1><p>All endpoints return JSON (unless noted) and are free to use. Amounts are given in flakes (1 SNOW = 1,000,000 flakes) unless a field is named <code>*_snow</code>.</p>");
    sb.append("<div class='tblwrap'><table class='tbl'><thead><tr><th>Endpoint</th><th>Description</th></tr></thead><tbody>");
    for (String[] d : DOCS)
    {
      String path = d[0];
      String link = path;
      int q = path.indexOf('{');
      if (q >= 0) link = null;
      int qq = path.indexOf('?');
      if (qq >= 0) link = path.substring(0, qq);
      sb.append("<tr><td class='mono'>");
      if (link != null) sb.append("<a href='").append(link).append("'>").append(Html.esc(path)).append("</a>");
      else sb.append(Html.esc(path));
      sb.append("</td><td class='wrap'>").append(Html.esc(d[1])).append("</td></tr>");
    }
    sb.append("</tbody></table></div>");
    sb.append("<h2 style='margin-top:16px'>Examples</h2><pre>curl https://snowblossom-explorer.org/api/status\ncurl https://snowblossom-explorer.org/api/block/0/435000\ncurl https://snowblossom-explorer.org/api/address/snow:2szcnkw6hlzp8fvhmnczermkzx7ffwaxzg9zpmdk?history=1</pre>");
    sb.append("</div>");
    return sb.toString();
  }

  /** @return true if handled */
  public boolean handle(WebContext t, String path, Map<String, String> q)
    throws Exception
  {
    t.setContentType("application/json; charset=utf-8");
    t.getExchange().getResponseHeaders().add("Access-Control-Allow-Origin", "*");
    t.getExchange().getResponseHeaders().add("Cache-Control", "no-cache");

    if (path.equals("/api/status"))
    {
      out(t, status());
      return true;
    }
    if (path.equals("/api/shards"))
    {
      out(t, shards());
      return true;
    }
    if (path.startsWith("/api/block/"))
    {
      String rest = path.substring("/api/block/".length());
      Block blk = null;
      String[] parts = rest.split("/");
      if ((parts.length == 2) && WebUtil.isDigits(parts[0]) && WebUtil.isDigits(parts[1]))
      {
        blk = shackleton.getCache().getBlockAtHeight(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
      }
      else if ((parts.length == 1) && (parts[0].length() == Globals.BLOCKCHAIN_HASH_LEN * 2) && WebUtil.isHex(parts[0]))
      {
        blk = shackleton.getCache().getBlock(new ChainHash(parts[0]));
      }
      if (blk == null)
      {
        error(t, 404, "block not found");
        return true;
      }
      out(t, block(blk, true));
      return true;
    }
    if (path.equals("/api/blocks"))
    {
      out(t, blocks(q));
      return true;
    }
    if (path.startsWith("/api/tx/"))
    {
      String hs = path.substring("/api/tx/".length());
      if ((hs.length() != Globals.BLOCKCHAIN_HASH_LEN * 2) || !WebUtil.isHex(hs))
      {
        error(t, 400, "invalid transaction hash");
        return true;
      }
      Transaction tx = shackleton.getCache().getTransaction(new ChainHash(hs));
      if (tx == null)
      {
        error(t, 404, "transaction not found");
        return true;
      }
      out(t, tx(tx));
      return true;
    }
    if (path.startsWith("/api/address/"))
    {
      String as = path.substring("/api/address/".length());
      AddressSpecHash addr = WebServer.parseAddress(shackleton.getParams(), as);
      if (addr == null)
      {
        error(t, 400, "invalid address");
        return true;
      }
      int count = WebUtil.clamp(WebUtil.intParam(q, "count", 100), 1, 1000);
      int offset = Math.max(0, WebUtil.intParam(q, "offset", 0));
      out(t, address(addr, q.containsKey("history"), count, offset, q.containsKey("utxos")));
      return true;
    }
    if (path.equals("/api/richlist"))
    {
      out(t, richlist(q));
      return true;
    }
    if (path.equals("/api/mining"))
    {
      out(t, mining());
      return true;
    }
    if (path.equals("/api/peers"))
    {
      out(t, peers());
      return true;
    }
    if (path.equals("/api/history"))
    {
      out(t, history(q));
      return true;
    }

    // ---------------------------------------------------------------- legacy
    if (path.equals("/api/total_coins"))
    {
      t.setContentType("text/plain");
      long total_value = shackleton.getRichList().getTotalValue();
      DecimalFormat df = new DecimalFormat("0.000000");
      t.out().println(df.format(total_value / 1e6));
      t.setHttpCode(200);
      return true;
    }
    if (path.equals("/api/total_coins_json"))
    {
      long total_value = shackleton.getRichList().getTotalValue();
      JSONObject obj = new JSONObject();
      obj.put("total_coins", total_value / 1e6);
      out(t, obj);
      return true;
    }
    if (path.equals("/api/recent_json_graph"))
    {
      out(t, WebServer.getNetworkGraph(shackleton, 10));
      return true;
    }
    if (path.equals("/api/health_stats"))
    {
      out(t, shackleton.getHealthStats().getHealthStats());
      return true;
    }
    if (path.startsWith("/api/block_info_by_height/"))
    {
      String[] parts = path.split("/");
      if (parts.length < 5)
      {
        error(t, 400, "expected /api/block_info_by_height/{shard}/{height}");
        return true;
      }
      int shard = Integer.parseInt(parts[3]);
      int height = Integer.parseInt(parts[4]);
      Block blk = shackleton.getCache().getBlockAtHeight(shard, height);
      if (blk == null)
      {
        error(t, 404, "Unable to find a block for shard " + shard + " height " + height);
        return true;
      }
      BlockSummary bs = shackleton.getCache().getSummary(new ChainHash(blk.getHeader().getSnowHash()));
      JSONObject obj = new JSONObject();
      obj.put("block_info", legacyBlockJson(bs, blk));
      out(t, obj);
      return true;
    }
    return false;
  }

  private void out(WebContext t, JSONObject obj)
  {
    t.out().println(obj.toJSONString());
    t.setHttpCode(200);
  }

  private void error(WebContext t, int code, String msg)
  {
    JSONObject o = new JSONObject();
    o.put("error", msg);
    t.out().println(o.toJSONString());
    t.setHttpCode(code);
  }

  // ------------------------------------------------------------------------
  public JSONObject status()
  {
    ChainIndex index = shackleton.getIndex();
    NetStats ns = index.getNetStats();
    JSONObject o = new JSONObject();
    o.put("network", shackleton.getParams().getNetworkName());
    o.put("generated_time", System.currentTimeMillis());
    if (ns == null)
    {
      o.put("ready", false);
      return o;
    }
    o.put("ready", true);
    o.put("index_updated", index.getLastUpdate());
    o.put("node_version", ns.node_version);
    o.put("mem_pool_size", ns.mem_pool_size);
    o.put("connected_peers", ns.connected_peers);
    o.put("estimated_nodes", ns.estimated_nodes);
    o.put("shard_count", ns.shard_count);
    o.put("max_height", ns.max_height);
    o.put("hashrate", ns.hashrate);
    o.put("avg_difficulty", ns.avg_diff);
    o.put("blocktime_average_ms", ns.blocktime_avg_ms);
    o.put("blocks_1h", ns.blocks_1h);
    o.put("tx_1h", ns.tx_1h);
    o.put("blocks_24h", ns.blocks_24h);
    o.put("tx_24h", ns.tx_24h);
    o.put("fees_24h", ns.fees_24h);
    o.put("coinbase_24h", ns.coinbase_24h);
    o.put("total_transactions", ns.total_transactions);
    o.put("activated_field", ns.activated_field);
    o.put("field_name", ns.field_name);
    if (ns.next_field_name != null)
    {
      o.put("next_field_name", ns.next_field_name);
      o.put("next_field_progress", ns.field_progress);
      o.put("next_field_hashrate_ratio", ns.field_hashrate_ratio);
      o.put("next_field_hashrate_factor", ns.field_hashrate_factor);
      o.put("field_activation_diff", ns.field_activation_diff);
      o.put("next_field_diff", ns.next_field_diff);
    }
    o.put("subsidy_per_height", ns.subsidy_per_height);
    RichList.Result rl = shackleton.getRichList().getResult();
    if (rl != null)
    {
      o.put("total_coins", rl.total / 1e6);
      o.put("address_count", rl.address_count);
      o.put("utxo_count", rl.utxo_count);
    }
    JSONObject versions = new JSONObject();
    for (Map.Entry<String, Integer> me : ns.version_map.entrySet()) versions.put(HexUtil.getSafeString(me.getKey()), me.getValue());
    o.put("peer_versions", versions);
    JSONObject shards = new JSONObject();
    for (ShardStats ss : index.getShardStats().values())
    {
      shards.put("" + ss.shard, shardStats(ss));
    }
    o.put("shards", shards);
    return o;
  }

  public static JSONObject shardStats(ShardStats ss)
  {
    JSONObject s = new JSONObject();
    s.put("shard", ss.shard);
    s.put("height", ss.height);
    s.put("head_hash", ss.head_info.hash.toString());
    s.put("timestamp", ss.timestamp);
    s.put("age_sec", ss.getAgeMs() / 1000);
    s.put("generation", ss.generation);
    s.put("parent", ss.parent);
    s.put("coordinator", ss.coordinator);
    s.put("building", ss.building);
    s.put("first_height", ss.first_height);
    s.put("blocks_1h", ss.blocks_1h);
    s.put("tx_1h", ss.tx_1h);
    s.put("blocks_24h", ss.blocks_24h);
    s.put("tx_24h", ss.tx_24h);
    s.put("fees_24h", ss.fees_24h);
    s.put("coinbase_24h", ss.coinbase_24h);
    s.put("target_diff", ss.target_diff);
    s.put("avg_diff", ss.avg_diff);
    s.put("hashrate", ss.hashrate);
    s.put("blocktime_average_ms", ss.blocktime_avg_ms);
    s.put("activated_field", ss.activated_field);
    s.put("tx_size_average", ss.tx_size_average);
    s.put("shard_length", ss.shard_length);
    s.put("total_transactions", ss.total_transactions);
    s.put("work_sum", ss.work_sum);
    s.put("subsidy", ss.subsidy);
    s.put("split_size_ratio", ss.split_size_ratio);
    s.put("split_length_ratio", ss.split_len_ratio);
    s.put("split_ready", ss.isSplitReady());
    return s;
  }

  public JSONObject shards()
  {
    ShardTree tree = shackleton.getIndex().getShardTree();
    JSONObject o = new JSONObject();
    NetworkParams params = shackleton.getParams();
    o.put("max_shard_id", params.getMaxShardId());
    o.put("min_shard_length", params.getMinShardLength());
    o.put("fork_threshold", params.getShardForkThreshold());
    o.put("activation_height", params.getActivationHeightShards());
    JSONArray arr = new JSONArray();
    if (tree != null)
    {
      o.put("max_generation", tree.max_generation);
      JSONArray leaves = new JSONArray();
      leaves.addAll(tree.leaves);
      o.put("active_shards", leaves);
      for (ShardNode n : tree.nodes.values())
      {
        JSONObject j = new JSONObject();
        j.put("shard", n.id);
        j.put("parent", n.parent);
        j.put("generation", n.generation);
        j.put("active", n.active);
        j.put("building", n.building);
        j.put("split", n.isRetired());
        JSONArray ch = new JSONArray();
        ch.addAll(n.children);
        j.put("children", ch);
        j.put("first_height", n.first_height);
        if (n.first_hash != null) j.put("first_hash", n.first_hash.toString());
        if (n.first_ts > 0) j.put("first_timestamp", n.first_ts);
        j.put("last_height", n.last_height);
        if (n.last_hash != null) j.put("last_hash", n.last_hash.toString());
        if (n.last_ts > 0) j.put("last_timestamp", n.last_ts);
        j.put("block_count", n.getBlockCount());
        arr.add(j);
      }
    }
    o.put("shards", arr);
    return o;
  }

  public JSONObject block(Block blk, boolean with_tx)
  {
    ChainCache cache = shackleton.getCache();
    BlockInfo bi = cache.getBlockInfo(blk);
    JSONObject o = bi.toJson();
    BlockHeader h = blk.getHeader();
    o.put("merkle_root_hash", HexUtil.getHexString(h.getMerkleRootHash()));
    o.put("utxo_root_hash", HexUtil.getHexString(h.getUtxoRootHash()));
    o.put("nonce", HexUtil.getHexString(h.getNonce()));
    o.put("target", HexUtil.getHexString(h.getTarget()));
    o.put("pow_proofs", h.getPowProofCount());
    int head = shackleton.getIndex().getHeightForConfirmations(bi.shard);
    if (head >= bi.height) o.put("confirmations", head - bi.height + 1);
    JSONObject imports = new JSONObject();
    for (Map.Entry<Integer, BlockImportList> me : new TreeMap<>(h.getShardImportMap()).entrySet())
    {
      JSONObject hm = new JSONObject();
      for (Map.Entry<Integer, ByteString> he : new TreeMap<>(me.getValue().getHeightMapMap()).entrySet())
      {
        hm.put("" + he.getKey(), HexUtil.getHexString(he.getValue()));
      }
      imports.put("" + me.getKey(), hm);
    }
    o.put("shard_imports", imports);
    JSONObject exports = new JSONObject();
    for (Map.Entry<Integer, ByteString> me : new TreeMap<>(h.getShardExportRootHashMap()).entrySet())
    {
      exports.put("" + me.getKey(), HexUtil.getHexString(me.getValue()));
    }
    o.put("shard_export_roots", exports);
    BlockSummary bs = cache.getSummary(bi.hash);
    if (bs != null)
    {
      JSONObject s = new JSONObject();
      s.put("work_sum", bs.getWorkSum());
      s.put("blocktime_average_ms", bs.getBlocktimeAverageMs());
      s.put("activated_field", bs.getActivatedField());
      s.put("total_transactions", bs.getTotalTransactions());
      s.put("tx_size_average", bs.getTxSizeAverage());
      s.put("shard_length", bs.getShardLength());
      s.put("avg_diff", PowUtil.getDiffForTarget(BlockchainUtil.readInteger(bs.getTargetAverage())));
      o.put("summary", s);
    }
    if (with_tx)
    {
      JSONArray txs = new JSONArray();
      for (Transaction tx : blk.getTransactionsList())
      {
        cache.cacheTransaction(tx);
        txs.add(TxView.of(tx, cache, blk.getTransactionsCount() <= 200).toJson(shackleton.getParams()));
      }
      o.put("transactions", txs);
    }
    return o;
  }

  public JSONObject blocks(Map<String, String> q)
  {
    ChainIndex index = shackleton.getIndex();
    ChainCache cache = shackleton.getCache();
    int count = WebUtil.clamp(WebUtil.intParam(q, "count", 50), 1, 500);
    JSONObject o = new JSONObject();
    JSONArray arr = new JSONArray();
    if (q.containsKey("shard"))
    {
      int shard = WebUtil.intParam(q, "shard", 0);
      int head = index.getHeightForConfirmations(shard);
      if (head < 0) head = cache.getShardHeight(shard);
      int from = WebUtil.intParam(q, "from", head);
      o.put("shard", shard);
      o.put("from", from);
      for (int h = from; (h > from - count) && (h >= 0); h--)
      {
        BlockInfo bi = cache.getBlockInfoAtHeight(shard, h);
        if (bi == null) break;
        arr.add(bi.toJson());
      }
    }
    else
    {
      int n = 0;
      for (BlockInfo bi : index.getRecentAll())
      {
        arr.add(bi.toJson());
        n++;
        if (n >= count) break;
      }
    }
    o.put("blocks", arr);
    return o;
  }

  public JSONObject tx(Transaction tx)
  {
    ChainCache cache = shackleton.getCache();
    TxView tv = TxView.of(tx, cache, true);
    JSONObject o = tv.toJson(shackleton.getParams());
    TxLocation loc = shackleton.getTxLocator().locate(tv);
    JSONObject st = new JSONObject();
    st.put("confirmed", loc.confirmed);
    st.put("mempool", loc.mempool);
    if (loc.confirmed)
    {
      st.put("shard", loc.shard);
      st.put("height", loc.height);
      if (loc.block_hash != null) st.put("block_hash", loc.block_hash.toString());
      if (loc.timestamp > 0) st.put("timestamp", loc.timestamp);
      st.put("confirmations", shackleton.getTxLocator().confirmations(loc));
    }
    o.put("status", st);
    return o;
  }

  public JSONObject address(AddressSpecHash addr, boolean history, int count, int offset, boolean with_utxos)
    throws Exception
  {
    NetworkParams params = shackleton.getParams();
    ChainCache cache = shackleton.getCache();
    JSONObject o = new JSONObject();
    o.put("address", addr.toAddressString(params));
    o.put("spec_hash", addr.toString());
    AddressBalance bal = AddressBalance.compute(shackleton, addr);
    o.put("balance_confirmed", bal.confirmed);
    o.put("balance_pending", bal.pending);
    o.put("balance_spendable", bal.spendable);
    o.put("balance_confirmed_snow", bal.confirmed / 1e6);
    o.put("utxo_count", bal.utxos);
    o.put("balance_method", bal.method);
    if (bal.snapshot_height >= 0) o.put("snapshot_height", bal.snapshot_height);
    if (bal.error != null) o.put("balance_error", bal.error);
    JSONObject shards = new JSONObject();
    for (Map.Entry<Integer, long[]> me : bal.per_shard.entrySet())
    {
      JSONObject s = new JSONObject();
      s.put("confirmed", me.getValue()[0]);
      s.put("pending", me.getValue()[1]);
      s.put("spendable", me.getValue()[2]);
      s.put("utxo_count", me.getValue()[3]);
      shards.put("" + me.getKey(), s);
    }
    o.put("shards", shards);
    JSONArray mp = new JSONArray();
    for (TxView tv : bal.mempool_txs) mp.add(tv.hash.toString());
    o.put("mempool_txs", mp);
    if (with_utxos)
    {
      JSONArray utxo_arr = new JSONArray();
      Map<String, TransactionBridge> bridges = shackleton.getUtxoUtil().getSpendableWithMempool(addr);
      for (TransactionBridge b : bridges.values())
      {
        if ((b.out == null) || (b.value < 0) || b.spent) continue;
        JSONObject u = new JSONObject();
        u.put("tx_hash", new ChainHash(b.in.getSrcTxId()).toString());
        u.put("out_idx", b.in.getSrcTxOutIdx());
        u.put("value", b.value);
        u.put("shard", b.shard_id);
        u.put("confirmed", !b.unconfirmed);
        utxo_arr.add(u);
      }
      o.put("utxos", utxo_arr);
    }
    RichList.Result rl = shackleton.getRichList().getResult();
    if (rl != null)
    {
      int rank = rl.rankOf(addr.toAddressString(params));
      if (rank > 0) o.put("richlist_rank", rank);
    }
    if (history)
    {
      HistoryList hl = shackleton.getStub().getAddressHistory(RequestAddress.newBuilder().setAddressSpecHash(addr.getBytes()).build());
      JSONArray arr = new JSONArray();
      o.put("history_enabled", !hl.getNotEnabled());
      TreeMap<String, HistoryEntry> uniq = new TreeMap<>();
      for (HistoryEntry he : hl.getEntriesList())
      {
        String key = String.format("%010d-%s", Integer.MAX_VALUE - he.getBlockHeight(), HexUtil.getHexString(he.getTxHash()));
        if (!uniq.containsKey(key)) uniq.put(key, he);
      }
      o.put("history_total", uniq.size());
      o.put("history_capped", hl.getEntriesCount() >= Globals.ADDRESS_HISTORY_MAX_REPLY);
      o.put("history_offset", offset);
      int skipped = 0;
      for (HistoryEntry he : uniq.values())
      {
        if (skipped < offset)
        {
          skipped++;
          continue;
        }
        if (arr.size() >= count) break;
        JSONObject j = new JSONObject();
        ChainHash txh = new ChainHash(he.getTxHash());
        ChainHash bh = new ChainHash(he.getBlockHash());
        j.put("tx_hash", txh.toString());
        j.put("block_hash", bh.toString());
        j.put("height", he.getBlockHeight());
        BlockHeader header = cache.getHeader(bh);
        if (header != null)
        {
          j.put("shard", header.getShardId());
          j.put("timestamp", header.getTimestamp());
        }
        Transaction tx = cache.getTransaction(txh);
        if (tx != null)
        {
          TxView tv = TxView.of(tx, cache, true);
          j.put("net_value", tv.netForAddress(addr));
          j.put("coinbase", tv.coinbase);
        }
        arr.add(j);
      }
      o.put("history", arr);
    }
    return o;
  }

  public JSONObject richlist(Map<String, String> q)
  {
    RichList.Result r = shackleton.getRichList().getResult();
    JSONObject o = new JSONObject();
    if (r == null)
    {
      o.put("ready", false);
      return o;
    }
    int count = WebUtil.clamp(WebUtil.intParam(q, "count", 100), 1, 1000);
    o.put("ready", true);
    o.put("generated_time", r.generated);
    o.put("max_height", r.max_height);
    o.put("total_coins", r.total / 1e6);
    o.put("total_flakes", r.total);
    o.put("address_count", r.address_count);
    o.put("utxo_count", r.utxo_count);
    o.put("top10", r.top10);
    o.put("top100", r.top100);
    o.put("top1000", r.top1000);
    JSONObject shards = new JSONObject();
    for (Map.Entry<Integer, Long> me : r.shard_totals.entrySet()) shards.put("" + me.getKey(), me.getValue());
    o.put("shard_totals", shards);
    JSONArray arr = new JSONArray();
    int n = 0;
    for (RichList.Entry e : r.entries)
    {
      JSONObject j = new JSONObject();
      j.put("rank", n + 1);
      j.put("address", e.address);
      j.put("balance", e.balance);
      j.put("balance_snow", e.balance / 1e6);
      j.put("utxos", e.utxos);
      arr.add(j);
      n++;
      if (n >= count) break;
    }
    o.put("entries", arr);
    return o;
  }

  public JSONObject mining()
  {
    MiningStats.Snapshot ms = shackleton.getMiningStats().getSnapshot();
    JSONObject o = new JSONObject();
    if (ms == null)
    {
      o.put("ready", false);
      return o;
    }
    o.put("ready", true);
    o.put("generated_time", ms.generated);
    o.put("blocks", ms.blocks);
    o.put("look_back_per_shard", ms.look_back);
    o.put("oldest_timestamp", ms.oldest_ts);
    o.put("newest_timestamp", ms.newest_ts);
    o.put("total_reward", ms.total_reward);
    o.put("total_fees", ms.total_fees);
    o.put("pools", entries(ms.remarks));
    o.put("miners", entries(ms.miners));
    JSONObject votes = new JSONObject();
    for (Map.Entry<Integer, MiningStats.VoteCount> me : ms.votes.entrySet())
    {
      JSONObject v = new JSONObject();
      v.put("yes", me.getValue().yes);
      v.put("no", me.getValue().no);
      v.put("both", me.getValue().both);
      votes.put("" + me.getKey(), v);
    }
    o.put("votes", votes);
    JSONObject bps = new JSONObject();
    for (Map.Entry<Integer, Integer> me : ms.blocks_per_shard.entrySet()) bps.put("" + me.getKey(), me.getValue());
    o.put("blocks_per_shard", bps);
    return o;
  }

  private static JSONArray entries(List<MiningStats.Entry> lst)
  {
    JSONArray arr = new JSONArray();
    for (MiningStats.Entry e : lst)
    {
      JSONObject j = new JSONObject();
      j.put("name", e.name);
      j.put("blocks", e.blocks);
      j.put("share", e.share);
      j.put("reward", e.reward);
      j.put("last_timestamp", e.last_ts);
      JSONArray s = new JSONArray();
      s.addAll(e.shards);
      j.put("shards", s);
      arr.add(j);
    }
    return arr;
  }

  public JSONObject peers()
  {
    JSONObject o = new JSONObject();
    JSONArray arr = new JSONArray();
    for (PeerInfo p : PeersPage.getPeers(shackleton))
    {
      JSONObject j = new JSONObject();
      j.put("host", p.getHost());
      j.put("port", p.getPort());
      j.put("version", HexUtil.getSafeString(p.getVersion()));
      j.put("connection_type", p.getConnectionType().toString());
      j.put("last_checked", p.getLastChecked());
      j.put("last_passed", p.getLastPassed());
      j.put("learned", p.getLearned());
      j.put("node_id", HexUtil.getHexString(p.getNodeId()));
      JSONArray s = new JSONArray();
      s.addAll(p.getShardIdSetList());
      j.put("shards", s);
      arr.add(j);
    }
    o.put("peers", arr);
    return o;
  }

  public JSONObject history(Map<String, String> q)
  {
    HistoryStats hs = shackleton.getHistoryStats();
    JSONObject o = new JSONObject();
    if (!hs.isReady())
    {
      o.put("ready", false);
      return o;
    }
    String[] rdef = HistoryStats.findRange(q.getOrDefault("range", "30d"));
    long range_ms = Long.parseLong(rdef[1]);
    long bucket_ms = Long.parseLong(rdef[2]);
    if (q.containsKey("days"))
    {
      int days = WebUtil.clamp(WebUtil.intParam(q, "days", 30), 1, hs.getDays());
      range_ms = days * HistoryStats.DAY_MS;
      int bh = WebUtil.clamp(WebUtil.intParam(q, "bucket_hours", 24), 1, 24 * 31);
      bucket_ms = bh * HistoryStats.HOUR_MS;
      if (range_ms / bucket_ms > 2000) bucket_ms = range_ms / 2000;
    }
    HistoryStats.Series r = hs.series(range_ms, bucket_ms);
    o.put("ready", true);
    o.put("generated_time", r.generated);
    o.put("range_ms", r.range_ms);
    o.put("bucket_ms", r.bucket_ms);
    o.put("history_days_available", hs.getDays());
    JSONArray arr = new JSONArray();
    for (int i = 0; i < r.starts.size(); i++)
    {
      JSONObject d = new JSONObject();
      d.put("start", r.starts.get(i));
      d.put("start_utc", Html.dateShort(r.starts.get(i)));
      d.put("total", bucketJson(r.total.get(i)));
      JSONObject shards = new JSONObject();
      for (Map.Entry<Integer, ? extends List<HistoryStats.Bucket>> me : r.per_shard.entrySet())
      {
        shards.put("" + me.getKey(), bucketJson(me.getValue().get(i)));
      }
      d.put("shards", shards);
      arr.add(d);
    }
    o.put("history", arr);
    return o;
  }

  private static JSONObject bucketJson(HistoryStats.Bucket d)
  {
    JSONObject j = new JSONObject();
    j.put("blocks", d.blocks);
    j.put("txs", d.txs);
    j.put("tx_data", d.tx_data);
    j.put("avg_diff", d.avgDiff());
    j.put("hashrate", d.hashrate());
    j.put("avg_block_time_sec", d.avgBlockTimeSec());
    j.put("covered_ms", d.length);
    return j;
  }

  private JSONObject legacyBlockJson(BlockSummary summary, Block blk)
  {
    BlockHeader bh = blk.getHeader();
    JSONObject info = new JSONObject();
    double avg_diff = (summary == null) ? 0 : PowUtil.getDiffForTarget(BlockchainUtil.readInteger(summary.getTargetAverage()));
    double block_diff = PowUtil.getDiffForTarget(BlockchainUtil.targetBytesToBigInteger(bh.getSnowHash()));
    double target_diff = PowUtil.getDiffForTarget(BlockchainUtil.targetBytesToBigInteger(bh.getTarget()));
    double block_time_sec = (summary == null) ? 0 : summary.getBlocktimeAverageMs() / 1000.0;
    double estimated_hash = (block_time_sec == 0) ? 0 : Math.pow(2.0, target_diff) / block_time_sec;
    info.put("hash", new ChainHash(bh.getSnowHash()).toString());
    info.put("shard", bh.getShardId());
    info.put("timestamp", bh.getTimestamp());
    info.put("height", bh.getBlockHeight());
    info.put("snow_field", bh.getSnowField());
    info.put("version", bh.getVersion());
    info.put("avg_diff", avg_diff);
    info.put("block_diff", block_diff);
    info.put("target_diff", target_diff);
    info.put("estimated_hash", estimated_hash);
    TransactionInner inner = TransactionUtil.getInner(blk.getTransactions(0));
    info.put("remark", HexUtil.getSafeString(inner.getCoinbaseExtras().getRemarks()));
    if (bh.getVersion() >= 2)
    {
      info.put("tx_count", bh.getTxCount());
      info.put("tx_data", bh.getTxDataSizeSum());
    }
    if (summary != null) info.put("blocktime_average_ms", summary.getBlocktimeAverageMs());
    return info;
  }
}
