package snowblossom.shackleton;

import com.google.common.collect.TreeMultimap;
import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import io.grpc.ManagedChannel;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Queue;
import java.util.AbstractCollection;

import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.client.GetUTXOUtil;
import snowblossom.client.StubHolder;
import snowblossom.client.StubUtil;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc;

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

    new RichList(config).print(System.out);



  }

  private UserServiceBlockingStub stub;
  private NetworkParams params;
  private GetUTXOUtil get_utxo_util;

  private volatile long last_total_value;

  public RichList(Config config)
    throws Exception
  { 

    params = NetworkParams.loadFromConfig(config);

    ManagedChannel channel = StubUtil.openChannel(config, params);

    stub = UserServiceGrpc.newBlockingStub(channel);

    get_utxo_util = new GetUTXOUtil(new StubHolder(channel), params);

  }

  public RichList(NetworkParams params, UserServiceBlockingStub stub, GetUTXOUtil get_utxo_util)
  {
    this.params = params;
    this.stub = stub;
    this.get_utxo_util = get_utxo_util;
  }

  public class BridgeMapper extends AbstractCollection<TransactionBridge>
  {
    HashMap<String, Long> balance_map = new HashMap<>();
    long total = 0L;

    @Override
    public boolean add(TransactionBridge br)
    {
      AddressSpecHash spec = new AddressSpecHash( br.out.getRecipientSpecHash());
      String addr = AddressUtil.getAddressString(params.getAddressPrefix(), spec);
      long val = 0;
      if (balance_map.containsKey(addr)) val = balance_map.get(addr);

      val += br.value;
      total += br.value;
      balance_map.put(addr, val);

      return true;
    }
    @Override
    public int size()
    {
      return 0;
    }

    @Override
    public Iterator<TransactionBridge> iterator()
    {
      return null;

    }
 
 
  }

  public void print(PrintStream out)
    throws Exception
  {
    NodeStatus node_status = stub.getNodeStatus(QueryUtil.nr());

    out.println("Highest block: " + node_status.getHeadSummary().getHeader().getBlockHeight());

    // TODO - cover all shards

    BridgeMapper bm = new BridgeMapper();

    GetUTXOUtil.getSpendableValidatedStatic(bm, ByteString.EMPTY, stub, node_status.getHeadSummary().getHeader().getUtxoRootHash(), 0);


    //getAllBlocks(node_status.getHeadSummary().getHeader());
    DecimalFormat df = new DecimalFormat("0.000000");

    HashMap<String, Long> balance_map = bm.balance_map;
    long total_value = bm.total;
    last_total_value = total_value;
    out.println("Total value: " + df.format(total_value / 1e6));

    
    out.println("Address count: " + balance_map.size());

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
        out.println("" + addr + " " + df.format(snow));
      }
      
    }
  } 

  public long getTotalValue()
    throws Exception
  {
    if (last_total_value == 0) throw new Exception("Not calculated yet");
    
    return last_total_value;
  }
}
