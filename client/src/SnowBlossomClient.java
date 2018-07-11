package snowblossom.client;

import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import snowblossom.lib.*;
import org.junit.Assert;
import snowblossom.proto.*;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import java.util.concurrent.atomic.AtomicLong;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import snowblossom.trie.proto.TrieNode;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Callable;
import duckutil.TaskMaster;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import duckutil.AtomicFileOutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SnowBlossomClient
{
  private static final Logger logger = Logger.getLogger("snowblossom.client");

  public static void main(String args[]) throws Exception
  {
    Globals.addCryptoProvider();

    if (args.length < 1)
    {
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: SnowBlossomClient <config_file> [commands]");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0]);

    LogSetup.setup(config);

    SnowBlossomClient client = new SnowBlossomClient(config);

    if (args.length > 1)
    {
      String command = args[1];

      if (command.equals("send"))
      {
        if (args.length < 4)
        {
          logger.log(Level.SEVERE, "Incorrect syntax. Syntax: SnowBlossomClient <config_file> send <amount> <dest_address>");
          System.exit(-1);
        }
        double val_snow = Double.parseDouble(args[2]);

        long value = (long) (val_snow * Globals.SNOW_VALUE);
        String to = args[3];

        DecimalFormat df = new DecimalFormat("0.000000");
        logger.info(String.format("Building send of %s to %s", df.format(val_snow), to));
        client.send(value, to);

      }
      else if (command.equals("balance"))
      {
            client.showBalances(false);

      }
      else if (command.equals("getfresh"))
      {
        boolean mark_used = false;
        boolean generate_now = false;
        if (args.length > 2)
        {
          mark_used = Boolean.parseBoolean(args[2]);
        }
        if (args.length > 3)
        {
          generate_now = Boolean.parseBoolean(args[3]);
        }

        AddressSpecHash hash  = client.getPurse().getUnusedAddress(mark_used, generate_now);
        String addr = AddressUtil.getAddressString(client.getParams().getAddressPrefix(), hash);
        System.out.println(addr);

      }
      else if (command.equals("monitor"))
      {
        while(true)
        {
          try
          {
            if (client == null)
            {
               client = new SnowBlossomClient(config);
            }
            client.showBalances(false);

          }
          catch(Throwable t)
          {
            t.printStackTrace();
            client = null;

          }
          Thread.sleep(60000);
        }
      }
      else if (command.equals("loadtest"))
      {
        client.runLoadTest();
      }
      else
      {
        logger.log(Level.SEVERE, String.format("Unknown command %s.  Try 'send'", command));
        System.exit(-1);
      }
    }
  }


  private final UserServiceStub asyncStub;
  private final UserServiceBlockingStub blockingStub;

	private final NetworkParams params;

  private File wallet_path;
  private Purse purse;
  private Config config;
  private ThreadPoolExecutor exec;

  public SnowBlossomClient(Config config) throws Exception
  {
    this.config = config;
    logger.info(String.format("Starting SnowBlossomClient version %s", Globals.VERSION));
    config.require("node_host");

    String host = config.get("node_host");
    params = NetworkParams.loadFromConfig(config);
    int port = config.getIntWithDefault("node_port", params.getDefaultPort());

    exec = TaskMaster.getBasicExecutor(64,"client_lookup");

    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();

    asyncStub = UserServiceGrpc.newStub(channel);
    blockingStub = UserServiceGrpc.newBlockingStub(channel);

    if (config.isSet("wallet_path"))
    {
      wallet_path = new File(config.get("wallet_path"));
      loadWallet();
      showBalances(false);
    }

  }

  public Purse getPurse(){return purse;}
  public NetworkParams getParams(){return params;}

  public void send(long value, String to)
    throws Exception
  {
    AddressSpecHash to_hash = AddressUtil.getHashForAddress(params.getAddressPrefix(), to);
    Transaction tx = TransactionUtil.makeTransaction(purse.getDB(), getAllSpendable(), 
      to_hash, value, 0L, 
      purse.getUnusedAddress(true,false));

    logger.info("Transaction: " + new ChainHash(tx.getTxHash()) + " - " + tx.toByteString().size());

    TransactionUtil.prettyDisplayTx(tx, System.out, params);

    //logger.info(tx.toString());

    System.out.println(blockingStub.submitTransaction(tx));

  }

  public void loadWallet()
    throws Exception
  {
    purse = new Purse(wallet_path, config, params);

  }

  public void showBalances(boolean print_each_address)
  {
    final AtomicLong total_confirmed = new AtomicLong(0);
    final AtomicLong total_unconfirmed = new AtomicLong(0L);
    final AtomicLong total_spendable = new AtomicLong(0L);
    final DecimalFormat df = new DecimalFormat("0.000000");

    Throwable logException = null;
    TaskMaster tm = new TaskMaster(exec);

    for(AddressSpec claim : purse.getDB().getAddressesList())
    {
      tm.addTask(new Callable(){
      public String call()
        throws Exception
      {
        AddressSpecHash hash = AddressUtil.getHashForSpec(claim);
        String address = AddressUtil.getAddressString(params.getAddressPrefix(), hash);
        StringBuilder sb = new StringBuilder();
        sb.append("Address: " + address + " - ");
        long value_confirmed = 0;
        long value_unconfirmed = 0;
        List<TransactionBridge> bridges = getSpendable(hash);
        if (bridges.size() > 0)
        {
          purse.markUsed(hash);
        }
        for(TransactionBridge b : bridges)
        {
          if (b.unconfirmed)
          {
            if (!b.spent)
            {
              value_unconfirmed += b.value;
            }
          }
          else //confirmed
          {
            value_confirmed += b.value;
            if (b.spent)
            {
              value_unconfirmed -= b.value;
            }
          }
          if (!b.spent)
          {
            total_spendable.addAndGet(b.value);
          }
        }

        double val_conf_d = (double) value_confirmed / (double) Globals.SNOW_VALUE;
        double val_unconf_d = (double) value_unconfirmed / (double) Globals.SNOW_VALUE;
        sb.append(String.format(" %s (%s pending) in %d outputs",
          df.format(val_conf_d), df.format(val_unconf_d), bridges.size()));
        total_confirmed.addAndGet(value_confirmed);
        total_unconfirmed.addAndGet(value_unconfirmed);
        return sb.toString();
      }

      });

    }

    List<String> addr_balances = tm.getResults();
    if (print_each_address)
    {
      for(String s : addr_balances) System.out.println(s);

    }


    double total_conf_d = (double) total_confirmed.get() / (double) Globals.SNOW_VALUE;
    double total_unconf_d = (double) total_unconfirmed.get() / (double) Globals.SNOW_VALUE;
    double total_spend_d = (double) total_spendable.get() / Globals.SNOW_VALUE_D;
    System.out.println(String.format("Total: %s (%s pending) (%s spendable)", df.format(total_conf_d), df.format(total_unconf_d),
      df.format(total_spend_d)));
  }

  public List<TransactionBridge> getAllSpendable()
    throws Exception
  {
    LinkedList<TransactionBridge> all = new LinkedList<>();
    for(AddressSpec claim : purse.getDB().getAddressesList())
    {
      AddressSpecHash hash = AddressUtil.getHashForSpec(claim);
      List<TransactionBridge> br_lst = getSpendable(hash);
      if (br_lst.size() > 0)
      {
        purse.markUsed(hash);
      }
      all.addAll(br_lst);
    }
    return all;
  }

  public List<TransactionBridge> getSpendable(AddressSpecHash addr)
  {

    GetUTXONodeReply reply = blockingStub.getUTXONode( GetUTXONodeRequest.newBuilder()
      .setPrefix(addr.getBytes())
      .setIncludeProof(true)
      .build());

    HashMap<String, TransactionBridge> bridge_map=new HashMap<>();

    for(TrieNode node : reply.getAnswerList())
    {
      if (node.getIsLeaf())
      {
        TransactionBridge b = new TransactionBridge(node);

        bridge_map.put(b.getKeyString(), b);
      }
    }

    for(ByteString tx_hash : blockingStub.getMempoolTransactionList(
      RequestAddress.newBuilder().setAddressSpecHash(addr.getBytes()).build()).getTxHashesList())
    {
      Transaction tx = blockingStub.getTransaction(RequestTransaction.newBuilder().setTxHash(tx_hash).build());

      TransactionInner inner = TransactionUtil.getInner(tx);

      for(TransactionInput in : inner.getInputsList())
      {
        if (addr.equals(in.getSpecHash()))
        {
          TransactionBridge b_in = new TransactionBridge(in);
          String key = b_in.getKeyString();
          if (bridge_map.containsKey(key))
          {
            bridge_map.get(key).spent=true;
          }
          else
          {
            bridge_map.put(key, b_in);
          }
        }
      }
      for(int o=0; o<inner.getOutputsCount(); o++)
      {
        TransactionOutput out = inner.getOutputs(o);
        if (addr.equals(out.getRecipientSpecHash()))
        {
          TransactionBridge b_out = new TransactionBridge(out, o, new ChainHash(tx_hash));
          String key = b_out.getKeyString();
          b_out.unconfirmed=true;

          if (bridge_map.containsKey(key))
          {
            if (bridge_map.get(key).spent)
            {
              b_out.spent=true;
            }
          }
          bridge_map.put(key, b_out);
        }
      }
    }


    LinkedList<TransactionBridge> lst = new LinkedList<>();
    lst.addAll(bridge_map.values());
    return lst;

  }

  public boolean submitTransaction(Transaction tx)
  {
    SubmitReply reply = blockingStub.submitTransaction(tx);

    return reply.getSuccess();
  }

  public void runLoadTest()
    throws Exception
  {
    LinkedList<TransactionBridge> spendable = new LinkedList<>();

    for(TransactionBridge br : getAllSpendable())
    {
      if (!br.spent) spendable.add(br);
    }
    Collections.shuffle(spendable);
    long min_send =  50000L;
    long max_send = 500000L;
    long send_delta = max_send - min_send;
    SplittableRandom rnd = new SplittableRandom();
    int output_count = 2;

    while(true)
    {
      //Collections.shuffle(spendable);

      LinkedList<TransactionOutput> out_list = new LinkedList<>();
      long fee = rnd.nextLong(500);
      fee = 0;
      long needed_value = fee;
      for(int i=0; i< output_count; i++)
      {
        long value = min_send + rnd.nextLong(send_delta);

        out_list.add( TransactionOutput.newBuilder()
          .setRecipientSpecHash(TransactionUtil.getRandomChangeAddress(purse.getDB()).getBytes() )
          .setValue(value)
          .build());
        needed_value+=value;
      }

      LinkedList<TransactionBridge> input_list = new LinkedList<>();
      while(needed_value > 0)
      {
        TransactionBridge b = spendable.pop();
        needed_value -= b.value;
        input_list.add(b);
      }

      Transaction tx = TransactionUtil.makeTransaction(purse.getDB(), input_list, out_list, fee, 
      purse.getUnusedAddress(true,false));
      if (tx == null)
      {
        logger.warning("Unable to make transaction");
        return;
      }
      TransactionInner inner = TransactionUtil.getInner(tx);

      ChainHash tx_hash = new ChainHash(tx.getTxHash());
      for(int i=0; i<inner.getOutputsCount(); i++)
      {
        TransactionBridge b = new TransactionBridge(inner.getOutputs(i), i, tx_hash);
        spendable.add(b);
      }

      logger.info("Transaction: " + new ChainHash(tx.getTxHash()) + " - " + tx.toByteString().size());
      //logger.info(tx.toString());

      boolean sent=false;
      while(!sent)
      {
        SubmitReply reply = blockingStub.submitTransaction(tx);
        if (reply.getSuccess())
        {
          sent=true;
        }
        else
        {
          logger.info("Error: " + reply.getErrorMessage());
          if (reply.getErrorMessage().contains("full"))
          {
            Thread.sleep(60000);
          }
          else
          {
            return;
          }
        }

      }
      boolean success = submitTransaction(tx);
      System.out.println("Submit: " + success);
      if (!success)
      {
        return;
      }
    }
  }
}
