package snowblossom.shackleton;

import com.google.protobuf.ByteString;
import com.google.protobuf.util.JsonFormat;
import duckutil.Config;
import duckutil.LRUCache;
import duckutil.SoftLRUCache;
import duckutil.webserver.DuckWebServer;
import duckutil.webserver.WebContext;
import duckutil.webserver.WebHandler;
import java.io.PrintStream;
import java.io.InputStream;
import java.net.URLConnection;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.TimeZone;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.proto.*;
import net.minidev.json.JSONObject;


public class WebServer implements WebHandler
{
  private static final Logger logger = Logger.getLogger("snowblossom.shackleton");
  private Shackleton shackleton;

  private LRUCache<ChainHash, String> block_summary_lines = new LRUCache<>(1000);
  private SoftLRUCache<ChainHash, BlockSummary> block_summary_cache = new SoftLRUCache<>(256 * 20);

  public WebServer(Config config, Shackleton shackleton)
    throws Exception
  {
    this.shackleton = shackleton;

    config.require("port");
    String host = null;
    int port = config.getInt("port");
    int threads = 32;

    new DuckWebServer(host, port, this, 32);

  }

  public void staticHandle(WebContext t)
    throws Exception
  {
    String path = t.getURI().getPath();

    path = path.substring(8); // remove the "/static/" 
    
    InputStream in = Shackleton.class.getResourceAsStream( "/shackleton/webstatic/" + path );
    if (in == null)
    {
      t.setContentType("text/plain");
      t.out().println("No resource: " + path);
      return;
    }
    else
    {
      String mime_type = Mimer.guessContentType(path);
      if (mime_type != null)
      {
        t.setContentType(mime_type);
      }
      
      byte[] buff = new byte[8192];

      while(true)
      {
        int r = in.read(buff);
        if (r < 0) break;
        t.out().write(buff,0,r);
      }
      in.close();
      t.out().flush();
      
      t.setHttpCode(200);


    }
  }

  public void apiHandle(WebContext t)
    throws Exception
  {
    String path = t.getURI().getPath();

    if (path.equals("/api") || path.equals("/api/"))
    {
      t.setContentType("text/html");
      t.getExchange().getResponseHeaders().add("Content-Language", "en-US");
      addHeader(t.out());
      t.out().println("<H2>APIs</H2>");
      t.out().println("<li><a href='/api/total_coins'>total_coins</a></li>");
      t.out().println("<li><a href='/api/recent_json_graph'>recent_json_graph</a></li>");
      t.out().println("<li><a href='/api/health_stats'>health_stats</a></li>");
      addFooter(t.out());
      t.setHttpCode(200);
      return;
    }

    if (path.equals("/api/total_coins"))
    {
      t.setContentType("text/plain");
      long total_value = shackleton.getTotalValue();
      DecimalFormat df = new DecimalFormat("0.000000");
      t.out().println(df.format(total_value / 1e6));
      t.setHttpCode(200);
      return;

    }
    if (path.equals("/api/recent_json_graph"))
    {
      t.setContentType("application/json");
      t.setHttpCode(200);
      t.out().println(getNetworkGraph(30));

      return;
    }
    if (path.equals("/api/health_stats"))
    {
      t.setContentType("application/json");
      t.setHttpCode(200);
      t.out().println( shackleton.getHealthStats().getHealthStats() );
      return;
    }

    t.setHttpCode(404);

  }

  public JSONObject getNetworkGraph(int blocks_back)
  {
    NodeStatus node_status = shackleton.getStub().getNodeStatus(QueryUtil.nr());
    Map<Integer, BlockSummary> bs_map = getShardSummaryMap(node_status);

    TreeSet<Integer> shards = new TreeSet<>();
    shards.addAll(bs_map.keySet());

    HashSet<ChainHash> included_blocks = new HashSet<>();
    LinkedList<BlockHeader> block_list = new LinkedList<>();


    for(int shard : shards)
    {
      int count_in_shard = 0;
      BlockSummary bs = bs_map.get(shard);
      while(
        (bs != null) && 
        (count_in_shard < blocks_back) &&
        (!included_blocks.contains(new ChainHash(bs.getHeader().getSnowHash())))
        )
      {
        ChainHash hash = new ChainHash(bs.getHeader().getSnowHash());
        included_blocks.add(hash);
        block_list.add(bs.getHeader());

        if (bs.getHeader().getBlockHeight() == 0) bs = null;
        else
        {
          bs = getBlockSummary( new ChainHash(bs.getHeader().getPrevBlockHash()));
        }
        count_in_shard++;

      }

    }

    return GraphOutput.getGraph(block_list);
 
  }

    public void innerHandle(WebContext t, PrintStream out)
      throws Exception
    {
      NetworkParams params = shackleton.getParams();
      String query = t.getURI().getQuery();
      logger.info("Web request: " + t.getURI() + " from " + t.getExchange().getRemoteAddress());
      if ((query != null) && (query.startsWith("search=")))
      {
        int eq = query.indexOf("=");
        String search = query.substring(eq+1);

        search = URLDecoder.decode(search.replace('+', ' '), "UTF-8").trim();

        // case: main page
        if (search.length() == 0)
        {
          displayStatus(out);
          return;
        }
        

        if (search.startsWith("load-"))
        {
          //try
          {
            displayAjax(out, Integer.parseInt(search.substring(5)));
            return;
          }
        }

        if (search.equals("richlist"))
        {
          displayRichList(out);
          return;
        }

        try
        {
          int block_num = Integer.parseInt(search);
          if (block_num >= 0)
          {
            displayBlockByNumber(out, block_num);
            return;
          }
        }
        catch(Throwable e){}

        // case: display block
        if (search.length()== Globals.BLOCKCHAIN_HASH_LEN*2)
        {
          ChainHash hash = null;
          try
          {
            hash = new ChainHash(search);

          }
          catch(Throwable e){}
          if (hash != null)
          {
            displayHash(out, hash);
            return;
          }
        }
        
        AddressSpecHash address = null;
        try
        {
          address = AddressUtil.getHashForAddress(params.getAddressPrefix(), search);
        }
        catch(Throwable e){}
        if (address == null)
        {
          if (search.length()== Globals.ADDRESS_SPEC_HASH_LEN*2)
          {
            try
            {
              address = new AddressSpecHash(search);
            }
            catch(Throwable e){}
          }
        }
        // case: address
        if (address != null)
        {
          displayAddress(out, address);
          return;
        }


        if (displayNameIdentifiers(out, search))
        {
          return;
        }

        // case: unknown
        displayUnexpectedStatus(out);
      }
      else
      {
        displayStatus(out);
      }

    }

  private boolean displayNameIdentifiers(PrintStream out, String search)
    throws ValidationException
  {

    TxOutList lst_chan = shackleton.getStub().getIDList(
      RequestNameID.newBuilder()
        .setNameType(RequestNameID.IdType.CHANNELNAME)
        .setName(ByteString.copyFrom(search.getBytes()))
        .build());

    TxOutList lst_user = shackleton.getStub().getIDList(
      RequestNameID.newBuilder()
        .setNameType(RequestNameID.IdType.USERNAME)
        .setName(ByteString.copyFrom(search.getBytes()))
        .build());

    if (lst_chan.getOutListCount() + lst_user.getOutListCount() == 0) return false;

    if (lst_chan.getOutListCount() > 0)
    {
      out.println("<H3>Channels</H3>");
      for(TxOutPoint op : lst_chan.getOutListList())
      {
        Transaction tx = shackleton.getStub().getTransaction( RequestTransaction.newBuilder().setTxHash(op.getTxHash()).build());
        displayTransaction(out, tx);
      }
    }
    if (lst_user.getOutListCount() > 0)
    {
      out.println("<H3>Users</H3>");
      for(TxOutPoint op : lst_user.getOutListList())
      {
        Transaction tx = shackleton.getStub().getTransaction( RequestTransaction.newBuilder().setTxHash(op.getTxHash()).build());
        displayTransaction(out, tx);
      }
    }
    
    return true; 


  }

  private void displayUnexpectedStatus(PrintStream out)
  {
    out.println("<div>No results</div>");
  }

  private void displayStatus(PrintStream out)
  {
    NetworkParams params = shackleton.getParams();
    NodeStatus node_status = shackleton.getStub().getNodeStatus(QueryUtil.nr());
    out.println("<h2>Node Status</h2>");
    out.println("<pre>");
    out.println("mem_pool_size: " + node_status.getMemPoolSize());
    out.println("connected_peers: " + node_status.getConnectedPeers());
    out.println("estimated_nodes: " + node_status.getEstimatedNodes());
    out.println("</pre>");

    BlockSummary summary = node_status.getHeadSummary();
    BlockHeader header = summary.getHeader();
    out.println("<h2>Braid Status</h2>");

    printBraidHeads(out, node_status);
    printBraidSummary(out, node_status);
    printBraidStatus(out, node_status);

    out.println("<h2>Chain Status</h2>");
    out.println("<pre>");

    SnowFieldInfo sf = params.getSnowFieldInfo(summary.getActivatedField());
    SnowFieldInfo next_sf = params.getSnowFieldInfo(summary.getActivatedField() + 1);
    double previous_diff = PowUtil.getDiffForTarget(sf.getActivationTarget());
    double avg_diff = PowUtil.getDiffForTarget(BlockchainUtil.readInteger(summary.getTargetAverage()));
    double next_diff = PowUtil.getDiffForTarget(next_sf.getActivationTarget());
    int percent_to_next_field = (int)Math.max(0, Math.round(100 * (avg_diff-previous_diff) / (next_diff-previous_diff)));

    out.println("work_sum: " + summary.getWorkSum());
    out.println("blocktime_average_ms: " + summary.getBlocktimeAverageMs());
    out.println("activated_field: " + summary.getActivatedField() + " " + sf.getName() + " (" + percent_to_next_field + "% to " + Math.round(next_diff) + ")");
    out.println("block_height: " + header.getBlockHeight());
    out.println("total_transactions: " + summary.getTotalTransactions());



    double target_diff = PowUtil.getDiffForTarget(BlockchainUtil.targetBytesToBigInteger(header.getTarget()));
    double block_time_sec = summary.getBlocktimeAverageMs() / 1000.0 ;
    double estimated_hash = Math.pow(2.0, target_diff) / block_time_sec / 1e6;
    DecimalFormat df =new DecimalFormat("0.000");
    df.setRoundingMode(RoundingMode.FLOOR);

    out.println(String.format("difficulty (avg): %s (%s)", df.format(target_diff), df.format(avg_diff)));
    out.println(String.format("estimated network hash rate: %s Mh/s", df.format(estimated_hash)));
    out.println("Node version: " + HexUtil.getSafeString(node_status.getNodeVersion()));
    out.println("Version counts:");
    for(Map.Entry<String, Integer> me : node_status.getVersionMap().entrySet())
    {
      String version = HexUtil.getSafeString(me.getKey());
      int count = me.getValue();
      out.println("  " + version + " - " + count);
    }

    out.println("</pre>");

    out.println("<h2>Calculator</h2>");
    out.println("<table style='width: 270px;'><tr><td>Hashrate</td><td><input type='text' id='hashrate' size=12 onChange='updateCalc();' onkeyup='updateCalc();'> kH/s</td></tr>");
    out.println("<tr><td>Per Hour</td><td><input type='text' id='hash1' value='0' size=12 disabled> Snow</td></tr>");
    out.println("<tr><td>Per Day</td><td><input type='text' id='hash2' value='0' size=12 disabled> Snow</td></tr>");
    out.println("<tr><td>Per Month</td><td><input type='text' id='hash3' value='0' size=12 disabled> Snow</td></tr>");
    out.println("</table>");
    out.println("<script>window.avgdiff=" + df.format(avg_diff) + ";</script>");
    
    out.println("<h2>Vote Status</h2>");
    out.println("<pre>");
    shackleton.getVoteTracker().getVotingReport(node_status, out);
    out.println("</pre>");

    out.println("<h2>Rich List</h2>");
    out.println("<pre><a href='?search=richlist'>Rich List Report</a></pre>");
    
    out.println("<h2>Visualization</h2>");
    out.println("<pre><a href='/static/shard-visual.html'>Shard Visualization</a></pre>");

    out.println("<h2>APIs</h2>");
    out.println("<pre><a href='/api'>APIs</a></pre>");

    out.println("<h2>Recent Blocks</h2>");
    int min = Math.max(0, header.getBlockHeight()-75);

    out.println("<table class='table table-hover' id='blocktable'>");
    out.println("<thead><tr><th>Shard</th><th>Height</th><th>Hash</th><th>Tx</th><th>Size</th><th>Miner</th><th>Remark</th><th>Timestamp</th></tr></thead>");

    for(int h=header.getBlockHeight(); h>=min; h--)
    {
      BlockHeader blk_head = shackleton.getStub().getBlockHeader(RequestBlockHeader.newBuilder().setBlockHeight(h).build());
      ChainHash hash = new ChainHash(blk_head.getSnowHash());
      out.println(getBlockSummaryLine(hash));
    }
    out.println("</table>");
    out.println("<a onclick='loadMore();' id='lmore'>Show more ...</a>");
    out.println("<script> window.lastblock=" + (min - 1) + "; </script>");
  }

  private void displayAjax(PrintStream out, int endblock)
  {
    int min=endblock - 100;
    for(int h=endblock; h > min; h--)
    {
      BlockHeader blk_head = shackleton.getStub().getBlockHeader(RequestBlockHeader.newBuilder().setBlockHeight(h).build());
      if (blk_head == null) break;
      if (blk_head.getSnowHash().size() == 0) break;
      ChainHash hash = new ChainHash(blk_head.getSnowHash());
      out.println(getBlockSummaryLine(hash));
    }
  }

  private void printChainSummary(BlockSummary summary, PrintStream out)
  {
    NetworkParams params = shackleton.getParams();

    BlockHeader header = summary.getHeader();
    out.println(String.format("<h3>Shard: %d</h3>", header.getShardId()));
    out.println("<pre>");

    SnowFieldInfo sf = params.getSnowFieldInfo(summary.getActivatedField());
    SnowFieldInfo next_sf = params.getSnowFieldInfo(summary.getActivatedField() + 1);
    double previous_diff = PowUtil.getDiffForTarget(sf.getActivationTarget());
    double avg_diff = PowUtil.getDiffForTarget(BlockchainUtil.readInteger(summary.getTargetAverage()));
    double next_diff = PowUtil.getDiffForTarget(next_sf.getActivationTarget());
    int percent_to_next_field = (int)Math.max(0, Math.round(100 * (avg_diff-previous_diff) / (next_diff-previous_diff)));

    out.println("work_sum: " + summary.getWorkSum());
    out.println("blocktime_average_ms: " + summary.getBlocktimeAverageMs());
    out.println("activated_field: " + summary.getActivatedField() + " " + sf.getName() + " (" + percent_to_next_field + "% to " + Math.round(next_diff) + ")");
    out.println("total_transactions: " + summary.getTotalTransactions());


    double target_diff = PowUtil.getDiffForTarget(BlockchainUtil.targetBytesToBigInteger(header.getTarget()));
    double block_time_sec = summary.getBlocktimeAverageMs() / 1000.0 ;
    double estimated_hash = Math.pow(2.0, target_diff) / block_time_sec / 1e6;
    DecimalFormat df =new DecimalFormat("0.000");
    df.setRoundingMode(RoundingMode.FLOOR);

    out.println(String.format("difficulty (avg): %s (%s)", df.format(target_diff), df.format(avg_diff)));
    out.println(String.format("estimated network hash rate: %s Mh/s", df.format(estimated_hash)));
 
    out.println("</pre>");
  }

  private String getBlockSummaryLine(ChainHash hash)
  {
    synchronized(block_summary_lines)
    {
      if (block_summary_lines.containsKey(hash))
      {
        return block_summary_lines.get(hash);
      }
    }

    NetworkParams params = shackleton.getParams();

    int tx_count = 0;
    int size = 0;
    String link = String.format(" <a href='/?search=%s'> %s </a>", hash, hash.toString());

    Block blk = shackleton.getStub().getBlock(RequestBlock.newBuilder().setBlockHash(hash.getBytes()).build());
    tx_count = blk.getTransactionsCount();
    size = blk.toByteString().size();

    TransactionInner inner = TransactionUtil.getInner(blk.getTransactions(0));

    String miner_addr = AddressUtil.getAddressString(params.getAddressPrefix(), new AddressSpecHash(inner.getOutputs(0).getRecipientSpecHash()));
    String remark = HexUtil.getSafeString(inner.getCoinbaseExtras().getRemarks());
    String miner = miner_addr;
    if (remark.length() > 0)
    {
      miner = miner_addr;
    }

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

    Date resultdate = new Date(blk.getHeader().getTimestamp());

    String s = String.format("<tr><td>%d</td><td>%d</td><td>%s</td><td>%d</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>",
        blk.getHeader().getShardId(),
        blk.getHeader().getBlockHeight(),
        link,
        tx_count, ((int)(size / 1024)) + "kB", "<a href='/?search=" + miner + "'>" + miner + "</a>", remark, sdf.format(resultdate) + " UTC");

    synchronized(block_summary_lines)
    {
      block_summary_lines.put(hash, s);
    }
    return s;
  }

  private void displayBlockByNumber(PrintStream out, int block_number)
    throws ValidationException
  {

    BlockHeader blk_head = shackleton.getStub().getBlockHeader(RequestBlockHeader.newBuilder().setBlockHeight(block_number).build());

    ChainHash hash = new ChainHash(blk_head.getSnowHash());
    displayHash(out, hash);

  }

  private void displayHash(PrintStream out, ChainHash hash)
    throws ValidationException
  {
    Block blk = shackleton.getStub().getBlock( RequestBlock.newBuilder().setBlockHash(hash.getBytes()).build());
    if ((blk!=null) && (blk.getHeader().getVersion() != 0))
    {
      displayBlock(out, blk);
    }
    else
    {
      Transaction tx = shackleton.getStub().getTransaction( RequestTransaction.newBuilder().setTxHash(hash.getBytes()).build());
      if (tx.getInnerData().size() > 0)
      {
        displayTransaction(out, tx);
      }
      else
      {
        out.println("Found nothing for hash: " + hash);
      }

    }

  }

  private void displayRichList(PrintStream out)
  {
    out.println("<pre>");
    out.println(shackleton.getRichListReport());
    out.println("</pre>");

  }
  private void displayAddress(PrintStream out, AddressSpecHash address)
  {
    NetworkParams params = shackleton.getParams();
    new AddressPage(out, address, params, shackleton.getStub(), true, shackleton.getUtxoUtil()).render();
  }

  private void displayBlock(PrintStream out, Block blk)
    throws ValidationException
  {
    BlockHeader header = blk.getHeader();
    out.println("<pre>");
    out.println("shard: "+ header.getShardId());
    out.println("hash: " + new ChainHash(header.getSnowHash()));
    out.println("height: " + header.getBlockHeight());
    out.println("prev_block_hash: " + new ChainHash(header.getPrevBlockHash()));
    out.println("utxo_root_hash: " + new ChainHash(header.getUtxoRootHash()));

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
    Date resultdate = new Date(header.getTimestamp());
    out.println("timestamp: " + header.getTimestamp() + " :: " + sdf.format(resultdate) + " UTC");
    out.println("snow_field: " + header.getSnowField());
    out.println("size: " + blk.toByteString().size());
    out.println();

    out.flush();

    for(Transaction tx : blk.getTransactionsList()) 
    {
      TransactionUtil.prettyDisplayTxHTML(tx, out, shackleton.getParams());
      out.println();
    }
  }


  private void printBraidSummary(PrintStream out, NodeStatus ns)
  {
    TreeSet<Integer> shards = new TreeSet<>();
    Map<Integer, BlockSummary> bs_map = getShardSummaryMap(ns);
    shards.addAll(ns.getShardHeadMap().keySet());
    for(int shard : shards)
    {
      BlockSummary bs_shard_head = bs_map.get(shard);
      if (bs_shard_head != null)
      {
        printChainSummary(bs_shard_head, out);
      }

    }

  }

  private Map<Integer, BlockSummary> getShardSummaryMap(NodeStatus ns)
  {
    TreeMap<Integer, BlockSummary> out = new TreeMap<>();
    for(Map.Entry<Integer, ByteString> me : ns.getShardHeadMap().entrySet())
    {
      int shard = me.getKey();
      ChainHash hash = new ChainHash(me.getValue());
      BlockSummary bs = getBlockSummary(hash);
      if (bs != null)
      {
        out.put(shard,bs);
      }

    }
    return out;

  }

  private void printBraidHeads(PrintStream out, NodeStatus ns)
  {

    Map<Integer, BlockSummary> bs_map = getShardSummaryMap(ns);
    TreeSet<Integer> shards = new TreeSet<>();
    shards.addAll(bs_map.keySet());
    System.out.println("Shard heads: " + shards + " " + bs_map.keySet());

    HashSet<ChainHash> included_blocks = new HashSet<>();
    out.println("<table class='table table-hover' id='blocktable'>");
    out.println("<thead><tr><th>Shard</th><th>Height</th><th>Hash</th><th>Tx</th><th>Size</th><th>Miner</th><th>Remark</th><th>Timestamp</th></tr></thead>");

    

    for(int shard : shards)
    {
      BlockSummary bs_shard_head = bs_map.get(shard);

      BlockSummary bs = bs_shard_head;
      if (bs != null)
      {
        ChainHash hash = new ChainHash(bs.getHeader().getSnowHash());
        included_blocks.add(hash);
        out.println(getBlockSummaryLine(hash));

        if (bs.getHeader().getBlockHeight() == 0) bs = null;
        else
        {
          bs = getBlockSummary( new ChainHash(bs.getHeader().getPrevBlockHash()));
        }

      }

    }
    out.println("</table>");

  }


  private void printBraidStatus(PrintStream out, NodeStatus ns)
  {
    long tx_count_4h = 0;
    long tx_count_1h = 0;
    long look_back_time_4h = 4L * 3600L * 1000L;
    long look_back_time_1h = 1L * 3600L * 1000L;
    long start_time = System.currentTimeMillis() - look_back_time_4h;
    long start_time_1h = System.currentTimeMillis() - look_back_time_1h;

    Map<Integer, BlockSummary> bs_map = getShardSummaryMap(ns);
    TreeSet<Integer> shards = new TreeSet<>();
    shards.addAll(bs_map.keySet());
    System.out.println("Shard list: " + shards);

    HashSet<ChainHash> included_blocks = new HashSet<>();
    out.println("<table class='table table-hover' id='blocktable'>");
    out.println("<thead><tr><th>Shard</th><th>Height</th><th>Hash</th><th>Tx</th><th>Size</th><th>Miner</th><th>Remark</th><th>Timestamp</th></tr></thead>");


    for(int shard : shards)
    {
      BlockSummary bs_shard_head = bs_map.get(shard);

      BlockSummary bs = bs_shard_head;
      while(
        (bs != null) && 
        (bs.getHeader().getTimestamp() >= start_time) &&
        (!included_blocks.contains(new ChainHash(bs.getHeader().getSnowHash())))
        )
      {
        ChainHash hash = new ChainHash(bs.getHeader().getSnowHash());
        included_blocks.add(hash);
        tx_count_4h += bs.getBlockTxCount();
        if (bs.getHeader().getTimestamp() >= start_time_1h)
        {
          tx_count_1h += bs.getBlockTxCount();

        }
        out.println(getBlockSummaryLine(hash));


        if (bs.getHeader().getBlockHeight() == 0) bs = null;
        else
        {
          bs = getBlockSummary( new ChainHash(bs.getHeader().getPrevBlockHash()));
        }

      }

    }
    out.println("</table>");

    DecimalFormat df = new DecimalFormat("0.00");
    out.println("<pre>");
    out.println("Transactions in last 4 hours: " + tx_count_4h);
    double rate = (tx_count_4h + 0.0) / (look_back_time_4h / 1000.0);
    out.println("Transaction per second last 4 hours: " + df.format(rate));

    out.println("Transactions in last hour: " + tx_count_1h);
    rate = (tx_count_1h + 0.0) / (look_back_time_1h / 1000.0);
    out.println("Transaction per second last hour: " + df.format(rate));


    out.println("</pre>");

  }

  public BlockSummary getBlockSummary(ChainHash hash)
  {
    synchronized(block_summary_cache)
    {
      if (block_summary_cache.containsKey(hash)) return block_summary_cache.get(hash);
    }

    try
    {
      BlockSummary bs = shackleton.getStub().getBlockSummary( 
        RequestBlockSummary.newBuilder().setBlockHash(hash.getBytes()).build());
      if (bs != null)
      {
        synchronized(block_summary_cache)
        {
          block_summary_cache.put(hash, bs);
        }
      }

      return bs;


    }
    catch(Exception e)
    {
      e.printStackTrace();
      return null;
    }
  }


  private void displayTransaction(PrintStream out, Transaction tx)
    throws ValidationException
  {
  out.println("<pre>");
  out.println("Found transaction");

  TransactionUtil.prettyDisplayTxHTML(tx, out, shackleton.getParams());
  out.println("");
  out.println("Transaction status:");

  try
  {
    TransactionStatus status = shackleton.getStub().getTransactionStatus(RequestTransaction.newBuilder().setTxHash(tx.getTxHash()).build());
    JsonFormat.Printer printer = JsonFormat.printer();
    out.println(printer.print(status));
  }
  catch(com.google.protobuf.InvalidProtocolBufferException e)
  {
    throw new ValidationException(e);
  }

  out.println("</pre>");

      
  }


  @Override
  public void handle(WebContext t) throws Exception
  {
    if (t.getURI().getPath().startsWith("/api"))
    {
      t.setHttpCode(404);
      apiHandle(t);

    }
    else if (t.getURI().getPath().startsWith("/static"))
    {
      t.setHttpCode(404);
      staticHandle(t);
    }
    else
    {
      t.setContentType("text/html");
      t.getExchange().getResponseHeaders().add("Content-Language", "en-US");
      boolean useajax = t.getURI().getQuery() != null && t.getURI().getQuery().contains("load-");
      if(!useajax) addHeader(t.out());
      innerHandle(t, t.out());
      if(!useajax) addFooter(t.out());
      t.setHttpCode(200);
    }


  }

    private void addHeader(PrintStream out)
    {
      NetworkParams params = shackleton.getParams();
      out.println("<html>");
      out.println("<head>");
      out.println("<meta charset='UTF-8'>");
      out.println("<title>" + params.getNetworkName() + " explorer</title>");
      out.println("<link rel='stylesheet' type='text/css' href='https://snowblossom.org/style-fixed.css' />");
      out.println("<link REL='SHORTCUT ICON' href='https://snowblossom.org/snow-icon.png' />");

      // True style comes from within
      //out.println("<link rel='stylesheet' href='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css' integrity='sha384-BVYiiSIFeK1dGmJRAkycuHAHRg32OmUcww7on3RYdg4Va+PmSTsz/K68vbdEjh4u' crossorigin='anonymous'>");
      //out.println("<link rel='stylesheet' href='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap-theme.min.css' integrity='sha384-rHyoN1iRsVXV4nD0JutlnGaslCJuC7uwjduW9SVrLvRYooPp2bWYgmgJQIXwl/Sp' crossorigin='anonymous'>");
      out.println("<script src='https://code.jquery.com/jquery-1.12.4.min.js' integrity='sha256-ZosEbRLbNQzLpnKIkEdrPv7lOy9C27hHQ+Xp8a4MxAQ=' crossorigin='anonymous'></script>");
      out.println("<script src='https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/js/bootstrap.min.js' integrity='sha384-Tc5IQib027qvyjSMfHjOMaLkfuWVxZxUPnCJA7l2mCWNIpG9mGCD8wGNIcPD7Txa' crossorigin='anonymous'></script>");

      out.println("<style type='text/css'>");
      out.println("input {text-align: right;}");
      out.println("</style>");
      out.println("<script>");
      out.println("window.doscroll = false; function loadMore() { if(window.doscroll) return; window.doscroll = true; $('#lmore').hide(); $.ajax( { url: '?search=load-' + window.lastblock, success: function (data) { $('#blocktable').append(data); $('#lmore').show(); }}); window.lastblock -= 100; setTimeout(function() {window.doscroll = false;}, 2000); };");
      out.println("$(window).scroll(function() { if(window.doscroll) return; if($(window).scrollTop() + 3000 > $(document).height()) {loadMore();}}); ");
      out.println("function updateCalc() {var hour = 50 * 3600 * parseFloat($('#hashrate').val()) / Math.pow(2,(window.avgdiff-10));$('#hash1').val(hour.toFixed(3));$('#hash2').val((hour*24).toFixed(3));$('#hash3').val((hour*24*30).toFixed(3));}");
      out.println("");
      out.println("</script>");
      out.println("</head>");
      out.println("<body>");
      out.print("<a href='/'>Home</a><br />");
      out.print("<form name='query' action='/' method='GET'>");
      out.print("<input type='text' name='search' size='45' value=''>");
      out.println("<input type='submit' value='Search'></form>");
      out.println("<br>");
    }

    private void addFooter(PrintStream out)
    {
      out.println("</body>");
      out.println("</html>");
    }


}
