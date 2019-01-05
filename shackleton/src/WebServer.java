package snowblossom.shackleton;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import duckutil.Config;
import snowblossom.lib.*;
import snowblossom.proto.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.math.RoundingMode;
import duckutil.TaskMaster;
import com.google.protobuf.util.JsonFormat;


public class WebServer
{
  private HttpServer server;
  private Shackleton shackleton;

  private LRUCache<ChainHash, String> block_summary_lines = new LRUCache<>(500);

  public WebServer(Config config, Shackleton shackleton)
		throws Exception
  {
    this.shackleton = shackleton;

    config.require("port");
    int port = config.getInt("port");

    server = HttpServer.create(new InetSocketAddress(port), 0);
		server.createContext("/", new RootHandler());
    server.setExecutor(TaskMaster.getBasicExecutor(32,"shackleton"));


  }

  public void start()
    throws java.io.IOException
  {
    server.start();
  }

  class RootHandler extends GeneralHandler
  {
    @Override
    public void innerHandle(HttpExchange t, PrintStream out)
      throws Exception
    {
      NetworkParams params = shackleton.getParams();
      String query = t.getRequestURI().getQuery();
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

        // case: unknown
        displayUnexpectedStatus(out);
      }
      else
      {
        displayStatus(out);
      }

    }
  }

  private void displayUnexpectedStatus(PrintStream out)
  {
    out.println("<div>Invalid search</div>");
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

    out.println("<h2>Vote Status</h2>");
    out.println("<pre>");
    shackleton.getVoteTracker().getVotingReport(node_status, out);
    out.println("</pre>");


    out.println("<h2>Recent Blocks</h2>");
    int min = Math.max(0, header.getBlockHeight()-75);
    out.println("<table border='0' cellspacing='0'>");
    out.println("<thead><tr><th>Height</th><th>Hash</th><th>Tx</th><th>Size</th><th>Miner</th><th>Timestamp</th></tr></thead>");
    for(int h=header.getBlockHeight(); h>=min; h--)
    {
      BlockHeader blk_head = shackleton.getStub().getBlockHeader(RequestBlockHeader.newBuilder().setBlockHeight(h).build());
      ChainHash hash = new ChainHash(blk_head.getSnowHash());
      out.println(getBlockSummaryLine(hash));
    }
    out.println("</table>");

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
    int size =0;
    String link = String.format(" <a href='/?search=%s'>B</a>", hash);

    Block blk = shackleton.getStub().getBlock(RequestBlock.newBuilder().setBlockHash(hash.getBytes()).build());
    tx_count = blk.getTransactionsCount();
    size = blk.toByteString().size();

    TransactionInner inner = TransactionUtil.getInner(blk.getTransactions(0));

    String miner_addr = AddressUtil.getAddressString(params.getAddressPrefix(), new AddressSpecHash(inner.getOutputs(0).getRecipientSpecHash()));
    String remark = HexUtil.getSafeString(inner.getCoinbaseExtras().getRemarks());
    String miner = miner_addr;
    if (remark.length() > 0)
    {
      miner = miner_addr + " - " + remark;
    }

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

    Date resultdate = new Date(blk.getHeader().getTimestamp());

    String s = String.format("<tr><td>%d</td><td>%s %s</td><td>%d</td><td>%d</td><td>%s</td><td>%s</td></tr>",
        blk.getHeader().getBlockHeight(), 
        hash.toString(), link,
        tx_count, size, miner, sdf.format(resultdate) + " UTC");

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
        TransactionUtil.prettyDisplayTx(tx, out, shackleton.getParams());
        out.println();
      }
      out.println("</pre>");
  }

  private void displayTransaction(PrintStream out, Transaction tx)
    throws ValidationException
  {
      out.println("<pre>");
      out.println("Found transaction");
      TransactionUtil.prettyDisplayTx(tx, out, shackleton.getParams());
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



  public abstract class GeneralHandler implements HttpHandler
  {
    @Override
    public void handle(HttpExchange t) throws IOException {

      t.getResponseHeaders().add("Content-Language", "en-US");
      ByteArrayOutputStream b_out = new ByteArrayOutputStream();
      PrintStream print_out = new PrintStream(b_out);
      try
      {
        addHeader(print_out);
        innerHandle(t, print_out);
        addFooter(print_out);
      }
      catch(Throwable e)
      {
        print_out.println("Exception: " + e);
      }

      byte[] data = b_out.toByteArray();
      t.sendResponseHeaders(200, data.length);
      OutputStream out = t.getResponseBody();
      out.write(data);
      out.close();

    }

    private void addHeader(PrintStream out)
    {
      NetworkParams params = shackleton.getParams();
      out.println("<html>");
      out.println("<head>");
      out.println("<title>" + params.getNetworkName() + " explorer</title>");
      out.println("<link rel='stylesheet' type='text/css' href='https://snowblossom.org/style-fixed.css' />");
      out.println("<link REL='SHORTCUT ICON' href='https://snowblossom.org/snow-icon.png' />");
      out.println("</head>");
      out.println("<body>");
      out.print("<a href='/'>House</a>");
      out.print("<form name='query' action='/' method='GET'>");
      out.print("<input type='text' name='search' size='45' value=''>");
      out.println("<input type='submit' value='Search'>");
      out.println("<br>");
    }
    private void addFooter(PrintStream out)
    {
      out.println("</body>");
      out.println("</html>");
    }

    public abstract void innerHandle(HttpExchange t, PrintStream out) throws Exception;
  }

}
