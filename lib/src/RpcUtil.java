package snowblossom.lib;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;

import net.minidev.json.JSONObject;
import net.minidev.json.JSONArray;

import duckutil.jsonrpc.JsonRpcServer;
import duckutil.jsonrpc.JsonRequestHandler;
import java.util.Map;

import com.google.protobuf.util.JsonFormat;
import net.minidev.json.parser.JSONParser;


import snowblossom.proto.*;

public class RpcUtil
{
  private NetworkParams params;

  public RpcUtil(NetworkParams params)
  {
    this.params = params;
  }

  public void registerHandlers(JsonRpcServer json_server)
  {
		json_server.register(new AddressToHashHandler());
		json_server.register(new HashToAddressHandler());
		json_server.register(new TransactionParseHandler());
    
  }

  public class AddressToHashHandler extends JsonRequestHandler
  {
    public String[] handledRequests()
    {
      return new String[]{"get_address_hash"};
    }

    @Override
    protected JSONObject processRequest(JSONRPC2Request req, MessageContext ctx)
      throws Exception
    {
      JSONObject reply = new JSONObject();
      String address = requireString(req, "address");

      AddressSpecHash spechash = new AddressSpecHash(address, params);

      reply.put("address", address);
      reply.put("spechash", HexUtil.getHexString(spechash.getBytes()));

      return reply;
    }
  }
  public class HashToAddressHandler extends JsonRequestHandler
  {
    public String[] handledRequests()
    {
      return new String[]{"get_hash_address"};
    }

    @Override
    protected JSONObject processRequest(JSONRPC2Request req, MessageContext ctx)
      throws Exception
    {
      JSONObject reply = new JSONObject();
      String spechash_str = requireString(req, "spechash");

      AddressSpecHash spechash = new AddressSpecHash(spechash_str);
      String address = spechash.toAddressString(params);

      reply.put("address", address);
      reply.put("spechash", HexUtil.getHexString(spechash.getBytes()));

      return reply;
    }
  }
  public class TransactionParseHandler extends JsonRequestHandler
  {
    public String[] handledRequests()
    {
      return new String[]{"parse_transaction"};
    }

    @Override
    protected JSONObject processRequest(JSONRPC2Request req, MessageContext ctx)
      throws Exception
    {
      JSONObject reply = new JSONObject();
      String tx_data = requireString(req, "tx_data");
      Transaction tx = Transaction.parseFrom( HexUtil.hexStringToBytes(tx_data) );
      TransactionInner inner = TransactionUtil.getInner(tx);

      reply.put("tx", protoToJson(tx));
			reply.put("inner", protoToJson(inner));

      return reply;
    }
  }



  public static String requireString(JSONRPC2Request req, String name)
    throws Exception
  {
    if (req.getNamedParams() == null) throw new Exception("params map must be included in request");
    Object o = req.getNamedParams().get(name);
    if (o == null) throw new Exception("String parameter " + name + " is required");
    if (!(o instanceof String)) throw new Exception("String parameter " + name + " is required");

    return (String)o;
  }

  public static JSONObject protoToJson(com.google.protobuf.Message m)
    throws Exception
  {
    JsonFormat.Printer printer = JsonFormat.printer();
    String str = printer.print(m);

    JSONParser parser = new JSONParser(JSONParser.MODE_STRICTEST);
    return (JSONObject) parser.parse(str);

  }

}
