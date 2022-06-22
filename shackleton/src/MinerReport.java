package snowblossom.shackleton;

import com.google.common.collect.TreeMultimap;
import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import io.grpc.ManagedChannel;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Collection;
import java.util.Random;
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

public class MinerReport
{
  private static final Logger logger = Logger.getLogger("snowblossom.shackleton");

  public static void main(String args[])
    throws Exception
  {
    Globals.addCryptoProvider();
    if (args.length != 1)
    { 
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: MinerReport <config_file>");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0],"snowblossom_");

    LogSetup.setup(config);

    new MinerReport(config).print(System.out);



  }

  private UserServiceBlockingStub stub;
  private NetworkParams params;
  private GetUTXOUtil get_utxo_util;

  private volatile long last_total_value;

  public MinerReport(Config config)
    throws Exception
  { 

    params = NetworkParams.loadFromConfig(config);
    ManagedChannel channel = StubUtil.openChannel(config, params);
    stub = UserServiceGrpc.newBlockingStub(channel);
    get_utxo_util = new GetUTXOUtil(new StubHolder(channel), params);

  }

  public MinerReport(NetworkParams params, UserServiceBlockingStub stub, GetUTXOUtil get_utxo_util)
  {
    this.params = params;
    this.stub = stub;
    this.get_utxo_util = get_utxo_util;
  }

  HashMap<AddressSpecHash, Long> value_map=new HashMap<>();
  long total_reward=0;
  HashSet<ChainHash> explored_blocks=new HashSet<>();

  public void print(PrintStream out)
    throws Exception
  {
    NodeStatus node_status = stub.getNodeStatus(QueryUtil.nr());
    out.println(node_status.getShardHeadMap());
    int depth=2000;
    for(int shard : node_status.getShardHeadMap().keySet())
    {
      out.println("Retreiving last " + depth + " blocks for shard " + shard);
      ChainHash block_hash = new ChainHash(node_status.getShardHeadMap().get(shard));
      exploreBlock(block_hash, depth);
    }
    DecimalFormat df = new DecimalFormat("0.000000");
    double reward_d = total_reward / Globals.SNOW_VALUE_D;
    out.println("Total reward: " + df.format(reward_d));
    int to_print=30;
    for(AddressSpecHash spec : getSortList())
    {
      String addr_s = spec.toAddressString( params);
      long val = value_map.get(spec);
      double ratio = (val +0.0) / (total_reward + 0.0);
      System.out.println("  " + addr_s + " " + df.format(val/Globals.SNOW_VALUE_D) + " " + ratio);

      to_print--;
      if (to_print <= 0) break;

    }

  }

  private void exploreBlock(ChainHash block_hash, int depth)
  {
    while(depth > 0)
    {
      if (explored_blocks.contains(block_hash)) return;
      explored_blocks.add(block_hash);

      Block b = stub.getBlock(RequestBlock.newBuilder().setBlockHash(block_hash.getBytes()).build());

      //System.out.println("" + b.getHeader().getShardId() + ":" +b.getHeader().getBlockHeight());

      processBlock(b);   

      if (b.getHeader().getBlockHeight()==0) return;
      block_hash = new ChainHash(b.getHeader().getPrevBlockHash());
      depth--;
    }
  }
  private void processBlock(Block b)
  {
    Transaction coinbase = b.getTransactions(0);

    TransactionInner c_inner = TransactionUtil.getInner(coinbase);
    if (!c_inner.getIsCoinbase()) throw new RuntimeException("not coinbase");
    
    for(TransactionOutput out : c_inner.getOutputsList())
    {
      long val = out.getValue();
      total_reward+=val;
      AddressSpecHash addr = new AddressSpecHash( out.getRecipientSpecHash() );

      if (!value_map.containsKey(addr)) value_map.put(addr, 0L);

      value_map.put(addr, value_map.get(addr) + val);
    }
  }

  private Collection<AddressSpecHash> getSortList()
  {
    TreeMap<Double, AddressSpecHash> sort_map = new TreeMap<>();

    Random rnd = new Random();
    for(Map.Entry<AddressSpecHash, Long> me : value_map.entrySet())
    {
      AddressSpecHash addr = me.getKey();
      long val = me.getValue();
      double key = val + rnd.nextDouble();
      sort_map.put(-key, addr);

    }
    return sort_map.values();

  }


}
