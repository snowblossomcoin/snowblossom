package snowblossom.shackleton;

import snowblossom.lib.*;
import snowblossom.proto.*;

import snowblossom.proto.UserServiceGrpc;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import duckutil.Config;
import duckutil.ConfigFile;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import com.google.common.collect.TreeMultimap;
import snowblossom.client.GetUTXOUtil;

import java.text.DecimalFormat;
import com.google.protobuf.ByteString;
import java.util.List;


public class RichList
{
  private static final Logger logger = Logger.getLogger("snowblossom.shackleton");

  public static void main(String args[])
    throws Exception
  {
    Globals.addCryptoProvider();
    if (args.length != 1)
    { 
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: RichList <config_file>");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0]);

    LogSetup.setup(config);

    new RichList(config);

  }

  private Config config;
  private UserServiceStub asyncStub;
  private UserServiceBlockingStub stub;
  private NetworkParams params;
  private GetUTXOUtil get_utxo_util;

  public RichList(Config config)
    throws Exception
  { 
    this.config = config;

    config.require("node_host");

    params = NetworkParams.loadFromConfig(config);

    String host = config.get("node_host");
    int port = config.getIntWithDefault("node_port", params.getDefaultPort());
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();

    asyncStub = UserServiceGrpc.newStub(channel);
    stub = UserServiceGrpc.newBlockingStub(channel);

    get_utxo_util = new GetUTXOUtil(stub, params);

    NodeStatus node_status = stub.getNodeStatus(QueryUtil.nr());

    System.out.println("Highest block: " + node_status.getHeadSummary().getHeader().getBlockHeight());

    List<TransactionBridge> bridge_list = GetUTXOUtil.getSpendableValidatedStatic( ByteString.EMPTY, stub, node_status.getHeadSummary().getHeader().getUtxoRootHash());


    //getAllBlocks(node_status.getHeadSummary().getHeader());
    DecimalFormat df = new DecimalFormat("0.000000");

    long total_value = getAddressBalances(bridge_list);
    System.out.println("Total value: " + df.format(total_value / 1e6));

    
    System.out.println("Address count: " + balance_map.size());

    TreeMultimap<Long, String> m = TreeMultimap.create();

    for(Map.Entry<String, Long> me : balance_map.entrySet())
    {
      m.put(-me.getValue(), me.getKey());
    }
    for(Map.Entry<Long, String> me : m.entries())
    {
      if (me.getKey() != 0L)
      {
        double val = -me.getKey();
        double snow = val / 1e6;
        String addr = me.getValue();
        System.out.println("" + addr + " " + df.format(snow));
      }
      
    }

    
  } 

  //HashSet<AddressSpecHash> all_addresses = new HashSet<>();
  HashMap<String, Long> balance_map = new HashMap<>();

  private long getAddressBalances(List<TransactionBridge> br_lst)
  {
    long total = 0;
    for(TransactionBridge br : br_lst)
    {
      AddressSpecHash spec = new AddressSpecHash( br.out.getRecipientSpecHash());
      String addr = AddressUtil.getAddressString(params.getAddressPrefix(), spec);
      long val = 0;
      if (balance_map.containsKey(addr)) val = balance_map.get(addr);

      val += br.value;
      total += br.value;
      balance_map.put(addr, val);
    }
    return total;

  }


  /*private void getAddressBalances()
  {
    for(AddressSpecHash spec : all_addresses)
    {
      String addr = AddressUtil.getAddressString(params.getAddressPrefix(), spec);
      AddressPage page = new AddressPage(System.out, spec, params, stub, false, get_utxo_util);
      page.loadData();
      balance_map.put(addr, page.valueConfirmed);
    }
  }

  private void getAllBlocks(BlockHeader start_header)
  {
    ChainHash hash = new ChainHash(start_header.getSnowHash());

    while(hash != null)
    {
      Block blk = stub.getBlock(RequestBlock.newBuilder().setBlockHash(hash.getBytes()).build());

      processBlock(blk);
      if (blk.getHeader().getBlockHeight() > 0)
      {
        hash = new ChainHash(blk.getHeader().getPrevBlockHash());
      }
      else
      {
        hash = null;
      }

    }

  }

  private void processBlock(Block blk)
  {
    for(Transaction tx : blk.getTransactionsList())
    {
      TransactionInner inner = TransactionUtil.getInner(tx);
      for(TransactionInput in : inner.getInputsList())
      {
        all_addresses.add(new AddressSpecHash(in.getSpecHash()));
      }
      for(TransactionOutput out : inner.getOutputsList())
      {
        all_addresses.add(new AddressSpecHash(out.getRecipientSpecHash()));
      }

    }

  }*/

  
}
