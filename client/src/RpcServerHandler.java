package snowblossom.client;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;

import net.minidev.json.JSONObject;
import net.minidev.json.JSONArray;

import snowblossom.proto.*;
import snowblossom.util.proto.*;
import snowblossom.lib.*;
import duckutil.jsonrpc.JsonRpcServer;
import duckutil.jsonrpc.JsonRequestHandler;
import java.util.Map;
import java.util.TreeMap;
import java.util.LinkedList;
import com.google.protobuf.util.JsonFormat;
import net.minidev.json.parser.JSONParser;

import com.google.common.collect.ImmutableSet;


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
      return new String[]{"send", "create_transaction"};
    }

    @Override
    protected JSONObject processRequest(JSONRPC2Request req, MessageContext ctx)
      throws Exception
    {
      JSONObject reply = new JSONObject();

      if (req.getNamedParams() == null)
      {
        throw new Exception("create_transaction requires parameters");
      }

      ImmutableSet<String> allowed_keys = ImmutableSet.of(
        "broadcast", "sign", "outputs",
        "change_random_from_wallet",
        "change_fresh_address",
        "change_specific_addresses",
        "change_addresses",
        "input_specific_list",
        "input_confirmed_then_pending",
        "input_confirmed_only",
        "inputs",
        "fee_flat",
        "fee_use_estimate",
        "extra",
        "split_change_over");
      Map<String, Object> params = req.getNamedParams();

      for(String s : params.keySet())
      {
        if (!allowed_keys.contains(s))
        {
          throw new Exception(String.format("Unknown parameter: %s", s));
        }
      }

      TransactionFactoryConfig.Builder tx_config = TransactionFactoryConfig.newBuilder();

      // Defaults
      tx_config.setChangeFreshAddress(true);
      tx_config.setInputConfirmedThenPending(true);
      tx_config.setFeeUseEstimate(true);
      tx_config.setSign(true);
      boolean broadcast = true;

      JSONArray output_list = (JSONArray) params.get("outputs");
      if (params.containsKey("broadcast")) { broadcast = (boolean) params.get("broadcast"); }
      if (params.containsKey("sign")) { tx_config.setSign( (boolean) params.get("sign") ); }
      if (params.containsKey("change_random_from_wallet")) { tx_config.setChangeRandomFromWallet( (boolean) params.get("change_random_from_wallet") ); }
      if (params.containsKey("change_fresh_address")) { tx_config.setChangeFreshAddress( (boolean) params.get("change_fresh_address") ); }
      if (params.containsKey("change_specific_addresses")) { tx_config.setChangeSpecificAddresses( (boolean) params.get("change_specific_addresses") ); }
      if (params.containsKey("input_specific_list")) { tx_config.setInputSpecificList( (boolean) params.get("input_specific_list") ); }
      if (params.containsKey("input_confirmed_then_pending")) { tx_config.setInputConfirmedThenPending( (boolean) params.get("input_confirmed_then_pending") ); }
      if (params.containsKey("input_confirmed_only")) { tx_config.setInputConfirmedOnly( (boolean) params.get("input_confirmed_only") ); }
      if (params.containsKey("fee_use_estimate")) { tx_config.setFeeUseEstimate( (boolean) params.get("fee_use_estimate") ); }

      
      if (params.containsKey("extra"))
      {
        String extra_str = (String) params.get("extra");
        tx_config.setExtra( HexUtil.stringToHex(extra_str) ); 
      }
      if (params.containsKey("fee_flat"))
      {
        double fee =  (double) params.get("fee_flat");
        tx_config.setFeeFlat(Math.round(fee * Globals.SNOW_VALUE));
      }
      if (params.containsKey("split_change_over"))
      {
        double split = (double) params.get("split_change_over");
        tx_config.setSplitChangeOver( Math.round(split * Globals.SNOW_VALUE) );
      }
      if (params.containsKey("change_addresses"))
      {
        JSONArray array = (JSONArray) params.get("change_addresses");
        for(Object o : array)
        {
          String address = (String) o;
          AddressSpecHash spec_hash = new AddressSpecHash(address, client.getParams());
          tx_config.addChangeAddresses(spec_hash.getBytes());

        }
      }
      if (params.containsKey("inputs"))
      {
        JSONArray array = (JSONArray) params.get("inputs");
        for(Object o : array)
        {
          Map<String, Object> m = (Map) o;
          UTXOEntry.Builder u = UTXOEntry.newBuilder();

          String address = (String) m.get("address");
          AddressSpecHash spec_hash = new AddressSpecHash(address, client.getParams());
          u.setSpecHash(spec_hash.getBytes());

          String tx_id = (String) m.get("src_tx");
          ChainHash tx_hash = new ChainHash( HexUtil.stringToHex(tx_id) );
          u.setSrcTx(tx_hash.getBytes());

          u.setSrcTxOutIdx( (int)(long) m.get("src_tx_out_idx") );
          u.setValue( (long) m.get("value") );

          tx_config.addInputs(u.build());
          
        }

      }

      LinkedList<TransactionOutput> tx_out_list = new LinkedList<>();
      for(Object obj : output_list)
      {
        Map<String, Object> out_entry = (Map) obj;
        String address = (String) out_entry.get("address");
        long flakes = 0L;

        if (out_entry.containsKey("flakes")) flakes = (long) out_entry.get("flakes");
        if (out_entry.containsKey("snow"))
        {
          double snow = (double) out_entry.get("snow");
          flakes = Math.round(snow * Globals.SNOW_VALUE);
        }
        AddressSpecHash spec_hash = new AddressSpecHash(address, client.getParams());
        tx_out_list.add(
          TransactionOutput.newBuilder()
            .setValue(flakes)
            .setRecipientSpecHash(spec_hash.getBytes())
            .build());
      }
      tx_config.addAllOutputs(tx_out_list);

      TransactionFactoryResult factory_result = TransactionFactory.createTransaction(
        tx_config.build(), 
        client.getPurse().getDB(),
        client);

      Transaction tx = factory_result.getTx();
      TransactionInner inner = TransactionUtil.getInner(tx);

      reply.put("tx_hash", HexUtil.getHexString(tx.getTxHash()));
      reply.put("tx_data", HexUtil.getHexString(tx.toByteString()));
      reply.put("fee", inner.getFee());
      reply.put("signatures_added", factory_result.getSignaturesAdded());
      reply.put("all_signed", factory_result.getAllSigned());
      if (broadcast)
      {
        client.sendOrException(tx);
      }

      /*JsonFormat.Printer printer = JsonFormat.printer();
      String json_str = printer.print(TransactionUtil.getInner(tx));

      JSONParser parser = new JSONParser(JSONParser.MODE_STRICTEST);
      
      JSONObject tx_json = (JSONObject)parser.parse(json_str);

      reply.put("tx_json", tx_json);*/

      return reply;
 
    }

  }

}

