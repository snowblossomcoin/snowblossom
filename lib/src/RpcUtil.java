package snowblossom.lib;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.util.JsonFormat;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import duckutil.jsonrpc.JsonRequestHandler;
import duckutil.jsonrpc.JsonRpcServer;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
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

      reply.put("tx", protoToJson(tx, params));
      reply.put("inner", protoToJson(inner, params));

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

  /**
   * params - Network parameters for address to string conversions
   */
  public static JSONObject protoToJson(com.google.protobuf.Message m, NetworkParams params)
    throws Exception
  {
    JsonFormat.Printer printer = JsonFormat.printer();
    String str = printer.print(m);

    JSONParser parser = new JSONParser(JSONParser.MODE_STRICTEST);
    JSONObject obj = (JSONObject) parser.parse(str);

    return addEncodedForms(obj, params);

  }

  // ChainHash forms: txHash snowHash utxoRootHash prevBlockHash srcTxId txHash
  // Address forms: specHash recipientSpecHash
  public static JSONObject addEncodedForms(JSONObject input, NetworkParams params)
  {
    JSONObject output = new JSONObject();

    Set<String> chainhash_forms = ImmutableSet.of("txHash","snowHash","utxoRootHash","prevBlockHash","srcTxId", "merkleRootHash");
    Set<String> addr_forms = ImmutableSet.of("specHash", "recipientSpecHash");

    for(Map.Entry<String, Object> me : input.entrySet())
    {
      String key = me.getKey();
      if (me.getValue() instanceof JSONObject)
      {
        output.put(key, addEncodedForms( (JSONObject) me.getValue(), params));
      }
      else if (me.getValue() instanceof JSONArray)
      {
        output.put(key, addEncodedFormsArray( (JSONArray) me.getValue(), params));
      }
      else if (me.getValue() instanceof String)
      {
        String v = (String)me.getValue();

        if (chainhash_forms.contains(key))
        {
          ChainHash hash = new ChainHash(Base64.getDecoder().decode(v));

          output.put(key+"Hex", hash.toString());
        }
        if (addr_forms.contains(key))
        {
          AddressSpecHash addr = new AddressSpecHash(Base64.getDecoder().decode(v));

          output.put(key+"Address", addr.toAddressString(params));

        }


        output.put(key, me.getValue());
      }
      else
      {
        output.put(key, me.getValue());
      }

    }

    return output;
  }

  
  public static JSONArray addEncodedFormsArray(JSONArray input, NetworkParams params)
  {
    JSONArray output = new JSONArray();

    for(Object o : input)
    {
      if (o instanceof JSONObject)
      {
        output.add( addEncodedForms( (JSONObject) o, params));
      }
      else if (o instanceof JSONArray)
      {
        output.add( addEncodedFormsArray( (JSONArray) o, params));
      }
      else
      {
        output.add(o);
      }

    }

    return output;
    
  }

}
