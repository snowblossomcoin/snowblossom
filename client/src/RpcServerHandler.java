package snowblossom.client;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;

import net.minidev.json.JSONObject;

import snowblossom.proto.*;
import snowblossom.lib.*;

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
      if (req.getNamedParams().containsKey("mark_used"))
      {
        mark_used = (boolean) req.getNamedParams().get("mark_used");
      }
      if (req.getNamedParams().containsKey("generate_now"))
      {
        generate_now = (boolean) req.getNamedParams().get("generate_now");
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



}
