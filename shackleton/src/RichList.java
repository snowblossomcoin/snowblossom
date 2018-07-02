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

import java.text.DecimalFormat;

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

    NodeStatus node_status = stub.getNodeStatus(QueryUtil.nr());

    System.out.println("Highest block: " + node_status.getHeadSummary().getHeader().getBlockHeight());

    getAllBlocks(node_status.getHeadSummary().getHeader());
    System.out.println("Address count: " + all_addresses.size());

    getAddressBalances();

    TreeMultimap<Long, String> m = TreeMultimap.create();

    for(Map.Entry<String, Long> me : balance_map.entrySet())
    {
      m.put(-me.getValue(), me.getKey());
    }
    DecimalFormat df = new DecimalFormat("0.000000");
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

  HashSet<AddressSpecHash> all_addresses = new HashSet<>();
  HashMap<String, Long> balance_map = new HashMap<>();

  private void getAddressBalances()
  {
    for(AddressSpecHash spec : all_addresses)
    {
      String addr = AddressUtil.getAddressString(params.getAddressPrefix(), spec);
      AddressPage page = new AddressPage(System.out, spec, params, stub, false);
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

  }

  
}
