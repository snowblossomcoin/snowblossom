package snowblossom.client;

import com.google.common.collect.ImmutableList;
import duckutil.Config;
import duckutil.AtomicFileOutputStream;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.KeyUtil;
import snowblossom.proto.*;

import com.google.protobuf.ByteString;


import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.LinkedList;
import snowblossom.lib.HexUtil;

public class WalletUtil
{
  public static final String MODE_STANDARD = "standard";
  public static final String MODE_QHARD = "qhard";

  private static final Logger logger = Logger.getLogger("snowblossom.client");
  public static final int WALLET_DB_VERSION = 2;

  public static WalletDatabase makeNewDatabase(Config config)
  {
    WalletDatabase.Builder builder = WalletDatabase.newBuilder();
    builder.setVersion(WALLET_DB_VERSION);

    int count = config.getIntWithDefault("key_count", 8);
    for (int i = 0; i < count; i++)
    {
      genNewKey(builder, config);
    }

    return builder.build();
  }

  public static void genNewKey(WalletDatabase.Builder wallet_builder, Config config)
  {
    String key_mode = config.getWithDefault("key_mode", MODE_STANDARD).toLowerCase();

    if (key_mode.equals(MODE_STANDARD))
    {
      WalletKeyPair wkp = KeyUtil.generateWalletStandardECKey();
      wallet_builder.addKeys(wkp);
      AddressSpec claim = AddressUtil.getSimpleSpecForKey(wkp);
      wallet_builder.addAddresses(claim);
    }
    else if (key_mode.equals(MODE_QHARD))
    {
      logger.info("Creating QHARD key set. This takes a while.");
      WalletKeyPair k_ec = KeyUtil.generateWalletStandardECKey();
      WalletKeyPair k_rsa = KeyUtil.generateWalletRSAKey(8192);
      WalletKeyPair k_dstu = KeyUtil.generateWalletDSTU4145Key(9);

      wallet_builder.addKeys(k_ec);
      wallet_builder.addKeys(k_rsa);
      wallet_builder.addKeys(k_dstu);

      AddressSpec claim = AddressUtil.getMultiSig(3, ImmutableList.of(k_ec, k_rsa, k_dstu));
      wallet_builder.addAddresses(claim);
    }
    else
    {
      throw new RuntimeException("Unknown key_mode: " + key_mode);
    }
  }

  public static WalletDatabase loadWallet(File wallet_path, boolean cleanup)
    throws Exception
  {
    if (!wallet_path.isDirectory()) return null;

    LinkedList<File> files_to_read = new LinkedList<>();

    { // old wallet.db file
      File old_file = new File(wallet_path, "wallet.db");
      if (old_file.exists()) files_to_read.add(old_file);
    }
    for(File f : wallet_path.listFiles())
    {
      if (f.getName().endsWith(".wallet"))
      {
        files_to_read.add(f);
      }
    }
    
    LinkedList<File> files_to_delete_on_cleanup=new LinkedList<>();
    LinkedList<WalletDatabase> found_db_list=new LinkedList<>();
    for(File f : files_to_read)
    {
      FileInputStream fin = new FileInputStream(f);
      WalletDatabase w = WalletDatabase.parseFrom(fin);
      fin.close();

      found_db_list.add(w);
      if (w.getVersion() <= WALLET_DB_VERSION)
      {
        files_to_delete_on_cleanup.add(f);
      }
      else
      {
        if (cleanup)
        {
          logger.info(String.format("Wallet db version %d for %s is newer than this code, so can't re-write it.  This is safe, but might lead to file clutter long term.", w.getVersion(), f.getPath()));
        }
      }

    }

    if (found_db_list.size() > 0)
    {
      WalletDatabase merged = mergeDatabases(found_db_list);

      if ((cleanup) && (found_db_list.size() > 1))
      {
        saveWallet(merged, wallet_path);
        for(File f : files_to_delete_on_cleanup)
        {
          f.delete();
        }
      }

      return merged;
    }


    return null;
  }

	public static WalletDatabase mergeDatabases(List<WalletDatabase> src_list)
  {
    WalletDatabase.Builder new_db = WalletDatabase.newBuilder();

    new_db.setVersion(WALLET_DB_VERSION);
    HashSet<WalletKeyPair> key_set=new HashSet<>();
    HashSet<AddressSpec> address_set =new HashSet<>();
    HashSet<Transaction> tx_set = new HashSet<>();
    HashMap<String, Boolean> used_addresses = new HashMap<>();
    HashMap<String, Long> address_create_time = new HashMap<>();

    for(WalletDatabase src : src_list)
    { 
      key_set.addAll(src.getKeysList());
      address_set.addAll(src.getAddressesList());
      tx_set.addAll(src.getTransactionsList());

      used_addresses.putAll(src.getUsedAddressesMap());
      address_create_time.putAll(src.getAddressCreateTimeMap());
    }

    new_db.addAllKeys(key_set);
    new_db.addAllAddresses(address_set);
    new_db.addAllTransactions(tx_set);
    new_db.putAllUsedAddresses(used_addresses);
    new_db.putAllAddressCreateTime(address_create_time);

    return new_db.build();
  }

  public static void saveWallet(WalletDatabase db, File wallet_path)
    throws Exception
  {
    wallet_path.mkdirs();

    Random rnd = new Random();
    byte[] rnd_data = new byte[6];
    rnd.nextBytes(rnd_data);
    String name = "snow-" + db.getVersion() + "_" + HexUtil.getHexString(rnd_data) + ".wallet";
    File db_file = new File(wallet_path, name);
    if (db_file.exists())
    {
      throw new RuntimeException("SOMETHING VERY UNLIKELY HAS OCCURED. ABORTING SAVE.");
    }
    AtomicFileOutputStream out = new AtomicFileOutputStream(db_file);

    db.writeTo(out);
    out.flush();
    out.close();

    logger.log(Level.INFO, String.format("Save to file %s completed", db_file.getPath()));
  }
}
