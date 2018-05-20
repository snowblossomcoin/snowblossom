package snowblossom.shackleton;

import snowblossom.Config;
import java.util.Map;

import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import snowblossom.proto.NullRequest;

public class WebServer
{
  private HttpServer server;
  private Shackleton shackleton;

  public WebServer(Config config, Shackleton shackleton)
		throws Exception
  {
    this.shackleton = shackleton;

    config.require("port");
    int port = config.getInt("port");

    server = HttpServer.create(new InetSocketAddress(port), 0);
		server.createContext("/", new RootHandler());
		server.createContext("/status", new RootHandler());
		server.createContext("/test", new MyHandler());
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
      {
        out.println(shackleton.getStub().getNodeStatus(nr()).toString());

      }
    }


    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = "This is the response";
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
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
        innerHandle(t, print_out);
      }
      catch(Throwable e)
      {
        print_out.println("Exception: " + e);
      }

      byte[] data = b_out.toByteArray();
      t.sendResponseHeaders(200, data.length);
      t.getResponseBody().write(data);


    }

    public abstract void innerHandle(HttpExchange t, PrintStream out) throws Exception;
  }

}
