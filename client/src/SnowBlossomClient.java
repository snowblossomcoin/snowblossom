package snowblossom.client;

import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import snowblossom.lib.*;
import org.junit.Assert;
import snowblossom.proto.*;
import snowblossom.util.proto.*;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import java.util.concurrent.atomic.AtomicLong;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import snowblossom.trie.proto.TrieNode;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Callable;
import duckutil.TaskMaster;
import java.io.PrintStream;
import duckutil.jsonrpc.JsonRpcServer;
import snowblossom.lib.trie.HashUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.Reader;
import java.io.InputStreamReader;
import duckutil.AtomicFileOutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.protobuf.util.JsonFormat;

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

    if (args.length == 1)
    {
      client.showBalances(false);

      client.printBasicStats(client.getPurse().getDB());

      System.out.println("Here is an unused address:");
      AddressSpecHash hash  = client.getPurse().getUnusedAddress(false, false);
      String addr = AddressUtil.getAddressString(client.getParams().getAddressPrefix(), hash);
      System.out.println(addr);
    }

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

        boolean send_all = false;
        String val_str = args[2];
        long value = 0L;
        double val_snow = 0.0;
        if (val_str.equals("all"))
        {
          send_all = true;
        }
        else
        {
          val_snow = Double.parseDouble(args[2]);
          value = (long) (val_snow * Globals.SNOW_VALUE);
        }
        String to = args[3];

        DecimalFormat df = new DecimalFormat("0.000000");
        if (send_all)
        {
          logger.info(String.format("Building send of ALL to %s", to));
        }
        else
        {
          logger.info(String.format("Building send of %s to %s", df.format(val_snow), to));

        }
        client.send(value, to, send_all);

      }
      else if (command.equals("sendlocked"))
      {
        if (args.length < 6)
        {
          logger.log(Level.SEVERE, "Incorrect syntax. Syntax: SnowBlossomClient <config_file> sendlocked <amount> <dest_address> <fbo_address> <block>");
          System.exit(-1);
        }
        double val_snow = Double.parseDouble(args[2]);

        long value = (long) (val_snow * Globals.SNOW_VALUE);
        String to = args[3];
        String fbo = args[4];
        int block = Integer.parseInt(args[5]);

        DecimalFormat df = new DecimalFormat("0.000000");
        logger.info(String.format("Building locked send of %s to %s for %s until %d", df.format(val_snow), to, fbo, block));
        client.sendLocked(value, to, fbo, block);

      }
 
      else if (command.equals("balance"))
      {
            client.showBalances(true);

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
        BalanceInfo bi_last = null;
        while(true)
        {
          try
          {
            if (client == null)
            {
               client = new SnowBlossomClient(config);
            }
            BalanceInfo bi = client.getBalance();
            if (!bi.equals(bi_last))
            {

              System.out.println("Total: " + getBalanceInfoPrint(bi));
              bi_last = bi;

            }


          }
          catch(Throwable t)
          {
            t.printStackTrace();
            client = null;

          }
          Thread.sleep(10000);
        }
      }
      else if (command.equals("rpcserver"))
      {
        JsonRpcServer json_server = new JsonRpcServer(config, true);
        RpcServerHandler server_handler = new RpcServerHandler(client);
        server_handler.registerHandlers(json_server);
        new RpcUtil(client.getParams()).registerHandlers(json_server);

        logger.info("RPC Server started");

        while(true)
        {
          Thread.sleep(1000);
        }
      }
      else if (command.equals("export"))
      {
        if (args.length != 3)
        {
          logger.log(Level.SEVERE, "export must be followed by filename to write to");
          System.exit(-1);
        }
        
        JsonFormat.Printer printer = JsonFormat.printer();
        AtomicFileOutputStream atomic_out = new AtomicFileOutputStream(args[2]);
        PrintStream print_out = new PrintStream(atomic_out);

        print_out.println(printer.print(client.getPurse().getDB()));
        print_out.close();
        
        logger.info(String.format("Wallet saved to %s", args[2]));
      }
      else if (command.equals("export_watch_only"))
      {
        if (args.length != 3)
        {
          logger.log(Level.SEVERE, "export must be followed by filename to write to");
          System.exit(-1);
        }
        
        JsonFormat.Printer printer = JsonFormat.printer();
        AtomicFileOutputStream atomic_out = new AtomicFileOutputStream(args[2]);
        PrintStream print_out = new PrintStream(atomic_out);

        WalletDatabase watch_db = WalletUtil.getWatchCopy(client.getPurse().getDB());


        print_out.println(printer.print(watch_db));
        print_out.close();
        
        logger.info(String.format("Wallet saved to %s", args[2]));
      }
 
      else if (command.equals("import"))
      {
        JsonFormat.Parser parser = JsonFormat.parser();
        WalletDatabase.Builder wallet_import = WalletDatabase.newBuilder();
        if (args.length != 3)
        {
          logger.log(Level.SEVERE, "import must be followed by filename to read from");
          System.exit(-1);
        }

        Reader input = new InputStreamReader(new FileInputStream(args[2]));
        parser.merge(input, wallet_import);
        if (config.getBoolean("watch_only") && (wallet_import.getKeysCount() > 0))
        {
          logger.log(Level.SEVERE, "Attempting to import wallet with keys into watch only wallet. Nope.");
          System.exit(-1);
        }

        WalletUtil.testWallet( wallet_import.build() );
        client.getPurse().mergeIn(wallet_import.build());

        logger.info("Imported data:");
        client.printBasicStats(wallet_import.build());
      }
      else if (command.equals("import_seed"))
      {
        System.out.println("Please enter seed to import:");
        Scanner scan = new Scanner(System.in);
        String seed = scan.nextLine().trim();

        SeedUtil.checkSeed(seed);
        ByteString seed_id = SeedUtil.getSeedId(client.getParams(), seed, "", 0);

        WalletDatabase.Builder wallet_import = WalletDatabase.newBuilder();
        wallet_import.putSeeds(seed, SeedStatus.newBuilder().setSeedId(seed_id).build());
        client.getPurse().mergeIn(wallet_import.build());

        client.getPurse().maintainKeys(true);

      }
      else if (command.equals("loadtest"))
      {
        client.runLoadTest();
      }
      else if (command.equals("nodestatus"))
      {
        NodeStatus ns = client.getNodeStatus();
        JsonFormat.Printer printer = JsonFormat.printer();
        System.out.println(printer.print(ns));
        
      }
      else if (command.equals("show_seed"))
      {
        WalletDatabase db = client.getPurse().getDB();
        HashSet<ByteString> seed_ids = new HashSet<>();
        for(String seed : db.getSeedsMap().keySet())
        {
          
          System.out.println("Seed: " + seed);
          seed_ids.add(db.getSeedsMap().get(seed).getSeedId());

        }
        int missing_keys = 0;
        for(WalletKeyPair wkp : db.getKeysList())
        {
          ByteString id = wkp.getSeedId();
          if (!seed_ids.contains(id)) missing_keys++;
        }
        if (missing_keys > 0)
        {
            System.out.println(String.format("WARNING: THIS WALLET CONTAINS %d KEYS THAT DO NOT COME FROM SEEDS.  THIS WALLET CAN NOT BE COMPLETELY RESTORED FROM SEEDS", missing_keys));
        }
        else
        {
          System.out.println("All keys in this wallet are derived from the seed(s) above and will be recoverable from those seeds.");
        }

      }
      else
      {
        logger.log(Level.SEVERE, String.format("Unknown command %s.", command));

        System.out.println("Commands:");
        System.out.println("(no command) - show total balance, show one fresh address");
        System.out.println("  balance - show balance of all addresses");
        System.out.println("  monitor - show balance and repeat");
        System.out.println("  getfresh [mark_used] [generate_now] - get a fresh address");
        System.out.println("    if mark_used is true, mark the address as used");
        System.out.println("    if generate_now is true, generate a new address rather than using the key pool");
        System.out.println("  send <amount> <destination> - send snow to address");
        System.out.println("  export <file> - export wallet to json file");
        System.out.println("  export_watch_only <file> - export wallet to json file with no keys");
        System.out.println("  import <file> - import wallet from json file, merges with existing");
        System.out.println("  import_seed - prompts for a seed to import");
        System.out.println("  show_seed - show seeds");
        System.out.println("  rpcserver - run a local rpc server for client commands");

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
  private GetUTXOUtil get_utxo_util;

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

    get_utxo_util = new GetUTXOUtil(blockingStub, params);

    if (config.isSet("wallet_path"))
    {
      wallet_path = new File(config.get("wallet_path"));
      loadWallet();
    }

  }

  public Purse getPurse(){return purse;}
  public NetworkParams getParams(){return params;}
  public Config getConfig(){return config;}

  public UserServiceBlockingStub getStub(){ return blockingStub; }

  public FeeEstimate getFeeEstimate()
  {
    return getStub().getFeeEstimate(NullRequest.newBuilder().build());
  }
  
  public void send(long value, String to, boolean send_all)
    throws Exception
  {

    TransactionFactoryConfig.Builder tx_config = TransactionFactoryConfig.newBuilder();

    tx_config.setSign(true);
    AddressSpecHash to_hash = AddressUtil.getHashForAddress(params.getAddressPrefix(), to);
    tx_config.addOutputs(TransactionOutput.newBuilder().setRecipientSpecHash(to_hash.getBytes()).setValue(value).build());
    tx_config.setChangeFreshAddress(true);
    tx_config.setInputConfirmedThenPending(true);
    tx_config.setFeeUseEstimate(true);
    tx_config.setSendAll(send_all);

    TransactionFactoryResult res = TransactionFactory.createTransaction(tx_config.build(), purse.getDB(), this);

    Transaction tx = res.getTx();


    logger.info("Transaction: " + new ChainHash(tx.getTxHash()) + " - " + tx.toByteString().size());

    TransactionUtil.prettyDisplayTx(tx, System.out, params);

    //logger.info(tx.toString());

    System.out.println(blockingStub.submitTransaction(tx));

  }
  public void sendLocked(long value, String to, String fbo, int block)
    throws Exception
  {

    TransactionFactoryConfig.Builder tx_config = TransactionFactoryConfig.newBuilder();

    tx_config.setSign(true);
    AddressSpecHash to_hash = AddressUtil.getHashForAddress(params.getAddressPrefix(), to);
    AddressSpecHash fbo_hash = AddressUtil.getHashForAddress(params.getAddressPrefix(), fbo);
    tx_config.addOutputs(TransactionOutput.newBuilder()
      .setRecipientSpecHash(to_hash.getBytes())
      .setValue(value)
      .setForBenefitOfSpecHash(fbo_hash.getBytes())
      .setRequirements( TransactionRequirements.newBuilder().setRequiredBlockHeight(block).build() )
      .build());
    tx_config.setChangeFreshAddress(true);
    tx_config.setInputConfirmedThenPending(true);
    tx_config.setFeeUseEstimate(true);

    TransactionFactoryResult res = TransactionFactory.createTransaction(tx_config.build(), purse.getDB(), this);

    Transaction tx = res.getTx();

    logger.info("Transaction: " + new ChainHash(tx.getTxHash()) + " - " + tx.toByteString().size());

    TransactionUtil.prettyDisplayTx(tx, System.out, params);

    //logger.info(tx.toString());

    System.out.println(blockingStub.submitTransaction(tx));

  }

 
  public void sendOrException(Transaction tx)
    throws Exception
  {
    SubmitReply res = blockingStub.submitTransaction(tx);

    if (!res.getSuccess())
    {
      throw new Exception("Submit transaction rejected: " + res.getErrorMessage());
    }

  }

  public void loadWallet()
    throws Exception
  {
    purse = new Purse(this, wallet_path, config, params);
    purse.maintainKeys(false);

  }

  public void printBasicStats(WalletDatabase db)
    throws ValidationException
  {
    int total_keys = db.getKeysCount();
    int total_addresses = db.getAddressesCount();
    int used_addresses = db.getUsedAddressesCount();
    int unused_addresses = total_addresses - used_addresses;

    System.out.println(String.format("Wallet Keys: %d, Addresses: %d, Fresh pool: %d", total_keys, total_addresses, unused_addresses));

    TreeMap<String, Integer> address_type_map = new TreeMap<>();
    for(AddressSpec spec : db.getAddressesList())
    {
      String type = AddressUtil.getAddressSpecTypeSummary(spec);
      if (address_type_map.containsKey(type))
      {
        address_type_map.put(type, 1 + address_type_map.get(type));
      }
      else
      {
        address_type_map.put(type, 1);
      } 
    }
    for(Map.Entry<String, Integer> me : address_type_map.entrySet())
    {
      System.out.println("  " + me.getKey() + ": " + me.getValue());
    }

  }

  public BalanceInfo getBalance(AddressSpecHash hash)
    throws Exception
  {
    long value_confirmed = 0;
    long value_unconfirmed = 0;
    long value_spendable = 0;
    boolean used=false;
    List<TransactionBridge> bridges = getSpendable(hash);
    if (bridges.size() > 0)
    {
      used=true;
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
        value_spendable += b.value;
      }
    }
    return BalanceInfo.newBuilder()
      .setConfirmed(value_confirmed)
      .setUnconfirmed(value_unconfirmed)
      .setSpendable(value_spendable)
      .build();
    
  }

  public BalanceInfo getBalance()
  {
    TaskMaster<BalanceInfo> tm = new TaskMaster(exec);

    for(AddressSpec claim : purse.getDB().getAddressesList())
    {
      tm.addTask(new Callable(){
      public BalanceInfo call()
        throws Exception
      {
        AddressSpecHash hash = AddressUtil.getHashForSpec(claim);
        BalanceInfo bi = getBalance(hash);

        return bi;
      }
      });
    }

    long total_confirmed = 0L;
    long total_unconfirmed = 0L;
    long total_spendable = 0L;
    for(BalanceInfo bi : tm.getResults())
    {
      total_confirmed += bi.getConfirmed();
      total_unconfirmed += bi.getUnconfirmed();
      total_spendable += bi.getSpendable();
    }

    return BalanceInfo.newBuilder()
      .setConfirmed(total_confirmed)
      .setUnconfirmed(total_unconfirmed)
      .setSpendable(total_spendable)
      .build();

     
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
        boolean used=false;
        List<TransactionBridge> bridges = getSpendable(hash);
        if (bridges.size() > 0)
        {
          used=true;
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
        if (purse.getDB().getUsedAddressesMap().containsKey(address))
        {
          used=true;
        }

        double val_conf_d = (double) value_confirmed / (double) Globals.SNOW_VALUE;
        double val_unconf_d = (double) value_unconfirmed / (double) Globals.SNOW_VALUE;
        sb.append(String.format(" %s (%s pending) in %d outputs",
          df.format(val_conf_d), df.format(val_unconf_d), bridges.size()));
        total_confirmed.addAndGet(value_confirmed);
        total_unconfirmed.addAndGet(value_unconfirmed);
        if (used)
        {
          return sb.toString();
        }
        return "";
      }

      });

    }

    List<String> addr_balances = tm.getResults();
    if (print_each_address)
    {
      Set<String> lines = new TreeSet<String>();
      lines.addAll(addr_balances);
      for(String s : lines)
      {
        if (s.length() > 0)
        {
          System.out.println(s);
        }
      }

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

  public NodeStatus getNodeStatus()
  {
    return blockingStub.getNodeStatus( NullRequest.newBuilder().build() );
  }

  public List<TransactionBridge> getSpendable(AddressSpecHash addr)
    throws ValidationException
  {

    Map<String, TransactionBridge> bridge_map= get_utxo_util.getSpendableWithMempool(addr);

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
    while(true)
    {
      runLoadTestInner();
    }
  }
  private void runLoadTestInner()
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

    while(true)
    {
      int output_count = 1;
      long fee = 7500;
      while (rnd.nextDouble() < 0.5) output_count++;
      //Collections.shuffle(spendable);

      LinkedList<TransactionOutput> out_list = new LinkedList<>();
      long needed_value = fee; //should cover a fee
      for(int i=0; i< output_count; i++)
      {
        long value = min_send + rnd.nextLong(send_delta);

        out_list.add( TransactionOutput.newBuilder()
          .setRecipientSpecHash(TransactionUtil.getRandomChangeAddress(purse.getDB()).getBytes() )
          .setValue(value)
          .build());
        needed_value+=value;
      }

      LinkedList<UTXOEntry> input_list = new LinkedList<>();
      while(needed_value > 0)
      {
        // This can happen because we are accumulating inputs as needed
        // but if the transaction maker uses the inputs in a different order
        // it might not use them all so we end up popping one and not using it
        // and thus forgetting about it.
        if (spendable.size() == 0)
        {
          logger.info("Out of inputs, resyncing");
          return;
        }
        TransactionBridge b = spendable.pop();
        needed_value -= b.value;
        input_list.add(b.toUTXOEntry());
      }

      TransactionFactoryConfig.Builder tx_config = TransactionFactoryConfig.newBuilder();

      tx_config.setSign(true);

      tx_config.addAllOutputs(out_list);
      tx_config.setChangeRandomFromWallet(true);
      tx_config.setInputSpecificList(true);
      tx_config.setFeeUseEstimate(true);
      //tx_config.setFeeUseEstimate(false);
      //tx_config.setFeeFlat(fee);
      tx_config.setSplitChangeOver(25000000L);
      tx_config.addAllInputs(input_list);

      TransactionFactoryResult res = TransactionFactory.createTransaction(tx_config.build(), purse.getDB(), this);

      Transaction tx = res.getTx();

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
      TransactionUtil.prettyDisplayTx(tx, System.out, params);
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
      Thread.sleep(100);
      if (!success)
      {
        return;
      }
    }
  }


  public static String getBalanceInfoPrint(BalanceInfo info)
  {
    DecimalFormat df = new DecimalFormat("0.000000");

    return String.format("%s (%s pending) (%s spendable)", 
      df.format(info.getConfirmed() / Globals.SNOW_VALUE_D),
      df.format(info.getUnconfirmed() / Globals.SNOW_VALUE_D),
      df.format(info.getSpendable() / Globals.SNOW_VALUE_D));

  }
}
