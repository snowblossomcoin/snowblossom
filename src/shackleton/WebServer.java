package snowblossom.shackleton;

import snowblossom.Config;
import snowblossom.NetworkParams;
import snowblossom.ChainHash;
import snowblossom.AddressSpecHash;
import snowblossom.AddressUtil;
import snowblossom.Globals;
import snowblossom.BlockchainUtil;
import snowblossom.PowUtil;
import snowblossom.LRUCache;
import java.util.Map;

import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;

import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import snowblossom.proto.NullRequest;
import snowblossom.proto.Block;
import snowblossom.proto.RequestBlock;
import snowblossom.proto.RequestTransaction;
import snowblossom.proto.Transaction;
import snowblossom.proto.NodeStatus;
import snowblossom.proto.BlockHeader;
import snowblossom.proto.BlockSummary;
import snowblossom.proto.RequestBlockHeader;

import snowblossom.TransactionUtil;
import snowblossom.ValidationException;

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
    server.setExecutor(null);


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
        if (search.length() == 0)
        {
          displayStatus(out);
          return;
        }

        if (search.length()==Globals.BLOCKCHAIN_HASH_LEN*2)
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
          address = AddressUtil.getHashForAddress( params.getAddressPrefix(), search);
        }
        catch(Throwable e){}
        if (address == null)
        {
          if (search.length()==Globals.ADDRESS_SPEC_HASH_LEN*2)
          {
            try
            {
              address = new AddressSpecHash(search);
            }
            catch(Throwable e){}
          }
        }
        if (address != null)
        {
          displayAddress(out, address);
          return;
        }


      }
      else
      {
        displayStatus(out);
      }

    }
  }

  private void displayStatus(PrintStream out)
  {
    NodeStatus node_status = shackleton.getStub().getNodeStatus(nr());
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

    out.println("work_sum: " + summary.getWorkSum());
    out.println("blocktime_average_ms: " + summary.getBlocktimeAverageMs());
    out.println("activated_field: " + summary.getActivatedField());
    out.println("block_height: " + header.getBlockHeight());
    out.println("total_transactions: " + summary.getTotalTransactions());

    double avg_diff = PowUtil.getDiffForTarget(BlockchainUtil.readInteger(summary.getTargetAverage()));
    double target_diff = PowUtil.getDiffForTarget(BlockchainUtil.targetBytesToBigInteger(header.getTarget()));
    DecimalFormat df =new DecimalFormat("0.00");

    out.println(String.format("difficulty (avg): %s (%s)", df.format(target_diff), df.format(avg_diff)));

    out.println("</pre>");


    out.println("<h2>Recent Blocks</h2>");
    int min = Math.max(0, header.getBlockHeight()-25);
    out.println("<table border='0' cellspacing='0'>");
    out.println("<thead><tr><th>Height</th><th>Hash</th><th>Transactions</th><th>Size</th></tr></thead>");
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

    int tx_count = 0;
    int size =0;
    String link = String.format(" <a href='/?search=%s'>B</a>", hash);

    Block blk = shackleton.getStub().getBlock(RequestBlock.newBuilder().setBlockHash(hash.getBytes()).build());
    tx_count = blk.getTransactionsCount();
    size = blk.toByteString().size();

    

    String s = String.format("<tr><td>%d</td><td>%s %s</td><td>%d</td><td>%d</td></tr>", 
        blk.getHeader().getBlockHeight(), 
        hash.toString(), link,
        tx_count, size);

    synchronized(block_summary_lines)
    {
      block_summary_lines.put(hash, s);
    }
    return s;
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
    out.println("Address: " + AddressUtil.getAddressString(params.getAddressPrefix(), address));
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
      out.println("timestamp: " + header.getTimestamp());
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
      out.println("</pre>");
  }



  public NullRequest nr()
  {
    return NullRequest.newBuilder().build();
  }

  public abstract class GeneralHandler implements HttpHandler
  {
    @Override
    public void handle(HttpExchange t) throws IOException {
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
      t.getResponseBody().write(data);


    }

    private void addHeader(PrintStream out)
    {
      NetworkParams params = shackleton.getParams();
      out.println("<html>");
      out.println("<head>");
      out.println("<title>Snowblossom " + params.getNetworkName() + "</title>");
      out.println("<link rel='stylesheet' type='text/css' href='https://snowblossom.org/style-fixed.css' />");
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
