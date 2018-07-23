package snowblossom.miner;

import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;

import net.minidev.json.JSONObject;

import snowblossom.proto.*;
import snowblossom.lib.*;
import duckutil.jsonrpc.JsonRpcServer;
import duckutil.jsonrpc.JsonRequestHandler;

import com.google.protobuf.ByteString;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;
import java.util.LinkedList;

public class MrPlowJsonHandler
{
  private MrPlow mr_plow;

  public MrPlowJsonHandler(MrPlow mr_plow)
  {
    this.mr_plow = mr_plow;
  }

  public void registerHandlers(JsonRpcServer json_server)
  {
    json_server.register(new GetFoundBlockHandler());
    json_server.register(new GetStatsHandler());

  }
  public class GetFoundBlockHandler extends JsonRequestHandler
  { 
    public String[] handledRequests()
    { 
      return new String[]{"getfoundblocks"};
    }

    @Override
    protected JSONObject processRequest(JSONRPC2Request req, MessageContext ctx)
      throws Exception
    { 

      List<ByteString> lst = mr_plow.getDB().getSpecialMapSet().getSet(MrPlow.BLOCK_KEY, 100000);
      TreeMap<Integer, BlockHeader> map = new TreeMap<>();
      for(ByteString bs : lst)
      {
        BlockHeader header = BlockHeader.parseFrom(bs);
        map.put(header.getBlockHeight(), header);
      }

      LinkedList<String> hash_list = new LinkedList<String>();
      for(Map.Entry<Integer, BlockHeader> me : map.entrySet())
      {
        hash_list.push(HexUtil.getHexString( me.getValue().getSnowHash()));
      }
      
      JSONObject reply = new JSONObject();
      reply.put("found_blocks", map.size());
      reply.put("hashes", hash_list);


      return reply;
    }
  }


  public class GetStatsHandler extends JsonRequestHandler
  { 
    public String[] handledRequests()
    { 
      return new String[]{"getstats"};
    }

    @Override
    protected JSONObject processRequest(JSONRPC2Request req, MessageContext ctx)
      throws Exception
    { 

      
      JSONObject reply = new JSONObject();

      int found_blocks = mr_plow.getDB().getSpecialMapSet().getSet(MrPlow.BLOCK_KEY, 100000).size();

      reply.put("found_blocks" , found_blocks);

      JSONObject rates = new JSONObject();
      mr_plow.getReportManager().writeReportJson(rates);


      reply.put("rates", rates);
      reply.put("share_map", mr_plow.getShareManager().getShareMap());
      reply.put("connections", mr_plow.getAgent().getMinerConnectionCount());


      return reply;
    }
  }



}
