package snowblossom.client;

import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.Assert;
import snowblossom.proto.*;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import snowblossom.trie.proto.TrieNode;
import snowblossomlib.TransactionUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
    snowblossomlib.Globals.addCryptoProvider();

    if (args.length < 1)
    {
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: SnowBlossomClient <config_file> [commands]");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0]);
    
    snowblossomlib.LogSetup.setup(config);

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

        long value = (long) (val_snow * snowblossomlib.Globals.SNOW_VALUE);
        String to = args[3];

        DecimalFormat df = new DecimalFormat("0.000000");
        logger.info(String.format("Building send of %s to %s", df.format(val_snow), to));
        client.send(value, to);

      }
      else if (command.equals("monitor"))
      {
        while(true)
        {
          Thread.sleep(60000);
          try
          {
            if (client == null)
            {
               client = new SnowBlossomClient(config);
            }
            client.showBalances();

          }
          catch(Throwable t)
          {
            t.printStackTrace();
            client = null;

          }
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

	private final snowblossomlib.NetworkParams params;

  private File wallet_path;
  private WalletDatabase wallet_database;
  private Config config;

  public SnowBlossomClient(Config config) throws Exception
  {
    this.config = config;
    config.require("node_host");

    String host = config.get("node_host");
    params = snowblossomlib.NetworkParams.loadFromConfig(config);
    int port = config.getIntWithDefault("node_port", params.getDefaultPort());

		
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();

    asyncStub = UserServiceGrpc.newStub(channel);
    blockingStub = UserServiceGrpc.newBlockingStub(channel);

    if (config.isSet("wallet_path"))
    {
      wallet_path = new File(config.get("wallet_path"));
      loadWallet();
      showBalances();
    }

  }

  public void send(long value, String to)
    throws Exception
  {
    snowblossomlib.AddressSpecHash to_hash = snowblossomlib.AddressUtil.getHashForAddress(params.getAddressPrefix(), to);
    Transaction tx = snowblossomlib.TransactionUtil.makeTransaction(wallet_database, getAllSpendable(), to_hash, value, 0L);

    logger.info("Transaction: " + new snowblossomlib.ChainHash(tx.getTxHash()) + " - " + tx.toByteString().size());

    snowblossomlib.TransactionUtil.prettyDisplayTx(tx, System.out, params);

    //logger.info(tx.toString());

    System.out.println(blockingStub.submitTransaction(tx));

  }

  public void loadWallet()
    throws Exception
  {
    wallet_path.mkdirs();

    File db_file = new File(wallet_path, "wallet.db");
    if (db_file.exists())
    {
      wallet_database = WalletDatabase.parseFrom(new FileInputStream(db_file));
    }
    else
    {
      logger.log(Level.WARNING, String.format("File %s does not exist, creating new wallet", db_file.getPath()));
      wallet_database = WalletUtil.makeNewDatabase(config);
      saveWallet();
    }

  }

  public void saveWallet()
    throws Exception
  {
    File db_file_tmp = new File(wallet_path, ".wallet.db.tmp");
    db_file_tmp.delete();

    FileOutputStream out = new FileOutputStream(db_file_tmp, false);
    wallet_database.writeTo(out);
    out.flush();
    out.close();

    FileInputStream re_read_in = new FileInputStream(db_file_tmp);
    WalletDatabase read = WalletDatabase.parseFrom(re_read_in);
    re_read_in.close();


    Assert.assertEquals(wallet_database, read);
    File db_file = new File(wallet_path, "wallet.db");

    Path db_file_path = FileSystems.getDefault().getPath(db_file.getPath());
    Path tmp_file_path = FileSystems.getDefault().getPath(db_file_tmp.getPath());

    Files.move(tmp_file_path, db_file_path, StandardCopyOption.REPLACE_EXISTING);


    //if (db_file_tmp.renameTo(db_file))
    //{
      logger.log(Level.INFO, String.format("Save to file %s completed", db_file.getPath()));
    //} 
    /*else
    {
      String msg = String.format("Unable to rename tmp file %s to %s", db_file_tmp.getPath(), db_file.getPath());

      logger.log(Level.WARNING, msg);
      throw new IOException(msg);
    }*/


  }

  public void showBalances()
  {
    long total_confirmed = 0;
    long total_unconfirmed = 0;
    long total_spendable = 0;
    DecimalFormat df = new DecimalFormat("0.000000");

    for(AddressSpec claim : wallet_database.getAddressesList())
    {
      snowblossomlib.AddressSpecHash hash = snowblossomlib.AddressUtil.getHashForSpec(claim);
      String address = snowblossomlib.AddressUtil.getAddressString(params.getAddressPrefix(), hash);
      System.out.print("Address: " + address + " - ");
      long value_confirmed = 0;
      long value_unconfirmed = 0;
      try
      {
        List<snowblossomlib.TransactionBridge> bridges = getSpendable(hash);

        for(snowblossomlib.TransactionBridge b : bridges)
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
            total_spendable += b.value;
          }

        }

        double val_conf_d = (double) value_confirmed / (double) snowblossomlib.Globals.SNOW_VALUE;
        double val_unconf_d = (double) value_unconfirmed / (double) snowblossomlib.Globals.SNOW_VALUE;
        System.out.println(String.format(" %s (%s pending) in %d outputs", 
          df.format(val_conf_d), df.format(val_unconf_d), bridges.size()));

        total_confirmed += value_confirmed;
        total_unconfirmed += value_unconfirmed;
      }
      catch(Throwable e)
      {
        System.out.println(e);
      }

    }
    double total_conf_d = (double) total_confirmed / (double) snowblossomlib.Globals.SNOW_VALUE;
    double total_unconf_d = (double) total_unconfirmed / (double) snowblossomlib.Globals.SNOW_VALUE;
    double total_spend_d = (double) total_spendable / snowblossomlib.Globals.SNOW_VALUE_D;
    System.out.println(String.format("Total: %s (%s pending) (%s spendable)", df.format(total_conf_d), df.format(total_unconf_d),
      df.format(total_spend_d)));
  }

  public List<snowblossomlib.TransactionBridge> getAllSpendable()
  {
    LinkedList<snowblossomlib.TransactionBridge> all = new LinkedList<>();
    for(AddressSpec claim : wallet_database.getAddressesList())
    {
      snowblossomlib.AddressSpecHash hash = snowblossomlib.AddressUtil.getHashForSpec(claim);
      all.addAll(getSpendable(hash));
    }
    return all;
  }

  public List<snowblossomlib.TransactionBridge> getSpendable(snowblossomlib.AddressSpecHash addr)
  {

    GetUTXONodeReply reply = blockingStub.getUTXONode( GetUTXONodeRequest.newBuilder()
      .setPrefix(addr.getBytes())
      .setIncludeProof(true)
      .build());

    HashMap<String, snowblossomlib.TransactionBridge> bridge_map=new HashMap<>();

    for(TrieNode node : reply.getAnswerList())
    {
      if (node.getIsLeaf())
      {
        snowblossomlib.TransactionBridge b = new snowblossomlib.TransactionBridge(node);

        bridge_map.put(b.getKeyString(), b);
      }
    }

    for(ByteString tx_hash : blockingStub.getMempoolTransactionList(
      RequestAddress.newBuilder().setAddressSpecHash(addr.getBytes()).build()).getTxHashesList())
    {
      Transaction tx = blockingStub.getTransaction(RequestTransaction.newBuilder().setTxHash(tx_hash).build());

      TransactionInner inner = snowblossomlib.TransactionUtil.getInner(tx);

      for(TransactionInput in : inner.getInputsList())
      {
        if (addr.equals(in.getSpecHash()))
        {
          snowblossomlib.TransactionBridge b_in = new snowblossomlib.TransactionBridge(in);
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
          snowblossomlib.TransactionBridge b_out = new snowblossomlib.TransactionBridge(out, o, new snowblossomlib.ChainHash(tx_hash));
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


    LinkedList<snowblossomlib.TransactionBridge> lst = new LinkedList<>();
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
    LinkedList<snowblossomlib.TransactionBridge> spendable = new LinkedList<>();

    for(snowblossomlib.TransactionBridge br : getAllSpendable())
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
      long needed_value = fee;
      for(int i=0; i< output_count; i++)
      {
        long value = min_send + rnd.nextLong(send_delta);

        out_list.add( TransactionOutput.newBuilder()
          .setRecipientSpecHash(snowblossomlib.TransactionUtil.getRandomChangeAddress(wallet_database).getBytes() )
          .setValue(value)
          .build());
        needed_value+=value;
      }

      LinkedList<snowblossomlib.TransactionBridge> input_list = new LinkedList<>();
      while(needed_value > 0)
      {
        snowblossomlib.TransactionBridge b = spendable.pop();
        needed_value -= b.value;
        input_list.add(b);
      }

      Transaction tx = snowblossomlib.TransactionUtil.makeTransaction(wallet_database, input_list, out_list, fee);
      if (tx == null)
      {
        logger.warning("Unable to make transaction");
        return;
      }
      TransactionInner inner = TransactionUtil.getInner(tx);

      snowblossomlib.ChainHash tx_hash = new snowblossomlib.ChainHash(tx.getTxHash());
      for(int i=0; i<inner.getOutputsCount(); i++)
      {
        snowblossomlib.TransactionBridge b = new snowblossomlib.TransactionBridge(inner.getOutputs(i), i, tx_hash);
        spendable.add(b);
      }

      logger.info("Transaction: " + new snowblossomlib.ChainHash(tx.getTxHash()) + " - " + tx.toByteString().size());
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
