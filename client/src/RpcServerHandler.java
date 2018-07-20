package snowblossom.client;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;

import net.minidev.json.JSONObject;

import snowblossom.proto.*;
import snowblossom.lib.*;
import duckutil.jsonrpc.JsonRpcServer;
import duckutil.jsonrpc.JsonRequestHandler;

public class RpcServerHandler
{
  private SnowBlossomClient client;

  public RpcServerHandler(SnowBlossomClient client)
  {
    this.client = client;
  }

  public void registerHandlers(JsonRpcServer json_server)
  {
		json_server.register(new GetFreshHandler());
		json_server.register(new BalanceHandler());
		json_server.register(new SendHandler());

  }

  public class GetFreshHandler extends JsonRequestHandler
  {
    public String[] handledRequests()
    {
      return new String[]{"getfresh"};
    }

    @Override
    protected JSONObject processRequest(JSONRPC2Request req, MessageContext ctx)
      throws Exception
    {
      boolean mark_used = false;
      boolean generate_now = false;
      if (req.getNamedParams() != null)
      {
        if (req.getNamedParams().containsKey("mark_used"))
        {
          mark_used = (boolean) req.getNamedParams().get("mark_used");
        }
        if (req.getNamedParams().containsKey("generate_now"))
        {
          generate_now = (boolean) req.getNamedParams().get("generate_now");
        }
      }
      JSONObject reply = new JSONObject();
      reply.put("mark_used", mark_used);
      reply.put("generate_now", generate_now);

      AddressSpecHash spec_hash = client.getPurse().getUnusedAddress(mark_used, generate_now);
      String address = AddressUtil.getAddressString(client.getParams().getAddressPrefix(), spec_hash);
      reply.put("address", address);

      return reply;
    }
  }

  public class BalanceHandler extends JsonRequestHandler
  {
    public String[] handledRequests()
    {
      return new String[]{"balance"};
    }

    @Override
    protected JSONObject processRequest(JSONRPC2Request req, MessageContext ctx)
      throws Exception
    {
      JSONObject reply = new JSONObject();

      BalanceInfo bi = client.getBalance();

      reply.put("flake_confirmed", bi.getConfirmed());
      reply.put("flake_unconfirmed", bi.getUnconfirmed());
      reply.put("flake_spendable", bi.getSpendable());

      reply.put("confirmed", bi.getConfirmed() / Globals.SNOW_VALUE_D);
      reply.put("unconfirmed", bi.getUnconfirmed() / Globals.SNOW_VALUE_D);
      reply.put("spendable", bi.getSpendable() / Globals.SNOW_VALUE_D);

      return reply;
 
    }

  }

  public class SendHandler extends JsonRequestHandler
  {
    public String[] handledRequests()
    {
      return new String[]{"send"};
    }

    @Override
    protected JSONObject processRequest(JSONRPC2Request req, MessageContext ctx)
      throws Exception
    {
      JSONObject reply = new JSONObject();

      


      return reply;
 
    }

  }


}

