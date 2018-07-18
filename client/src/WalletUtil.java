package snowblossom.client;

import com.google.common.collect.ImmutableList;
import duckutil.Config;
import duckutil.AtomicFileOutputStream;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.KeyUtil;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.AddressSpecHash;
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
import com.google.common.collect.TreeMultimap;
import java.util.TreeMap;
import java.util.Map;

public class WalletUtil
{
  public static final String MODE_STANDARD = "standard";
  public static final String MODE_QHARD = "qhard";

  private static final Logger logger = Logger.getLogger("snowblossom.client");
  public static final int WALLET_DB_VERSION = 2;

  public static WalletDatabase makeNewDatabase(Config config, NetworkParams params)
  {
    WalletDatabase.Builder builder = WalletDatabase.newBuilder();
    builder.setVersion(WALLET_DB_VERSION);

    int count = config.getIntWithDefault("key_count", 8);
    for (int i = 0; i < count; i++)
    {
      genNewKey(builder, config, params);
    }

    return builder.build();
  }

  public static void genNewKey(WalletDatabase.Builder wallet_builder, Config config, NetworkParams params)
  {
    String key_mode = config.getWithDefault("key_mode", MODE_STANDARD).toLowerCase();
    AddressSpec claim = null;

    if (key_mode.equals(MODE_STANDARD))
    {
      WalletKeyPair wkp = KeyUtil.generateWalletStandardECKey();
      wallet_builder.addKeys(wkp);
      claim = AddressUtil.getSimpleSpecForKey(wkp);
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

      claim = AddressUtil.getMultiSig(3, ImmutableList.of(k_ec, k_rsa, k_dstu));
      wallet_builder.addAddresses(claim);
      
    }
    else
    {
      throw new RuntimeException("Unknown key_mode: " + key_mode);
    }

    wallet_builder.putAddressCreateTime( AddressUtil.getAddressString(claim, params), System.currentTimeMillis());
  }

  public static WalletDatabase fillKeyPool(WalletDatabase existing_db, File wallet_path, Config config, NetworkParams params)
    throws Exception
  {
    int key_pool = config.getIntWithDefault("key_pool_size", 100);
    int unused = getUnusedAddressCount(existing_db, params);
    if (unused < key_pool)
    {
      int to_make = key_pool - unused;
      WalletDatabase.Builder partial_new_db = WalletDatabase.newBuilder();
      partial_new_db.setVersion(WALLET_DB_VERSION);
      for(int i=0; i<to_make; i++)
      {
        genNewKey(partial_new_db, config, params);
      }
      WalletDatabase new_db_part = partial_new_db.build();
      saveWallet(new_db_part, wallet_path);

      return mergeDatabases(ImmutableList.of(existing_db, new_db_part));

    }
    return existing_db;
  }


  public static WalletDatabase markUsed(WalletDatabase existing_db, File wallet_path, Config config, NetworkParams params, AddressSpecHash hash)
    throws Exception
  {
    String address = AddressUtil.getAddressString(params.getAddressPrefix(), hash);

    return markUsed(existing_db, wallet_path, config, params, address);
  }
  public static WalletDatabase markUsed(WalletDatabase existing_db, File wallet_path, Config config, NetworkParams params, String address)
    throws Exception
  {
    if (existing_db.getUsedAddressesMap().containsKey(address)) return existing_db;
    WalletDatabase.Builder partial_new_db = WalletDatabase.newBuilder();
    partial_new_db.setVersion(WalletUtil.WALLET_DB_VERSION);
    partial_new_db.putUsedAddresses(address, true);

    WalletDatabase new_db_part = partial_new_db.build();
    saveWallet(new_db_part, wallet_path);

    return mergeDatabases(ImmutableList.of(existing_db, new_db_part));

  }

  public static AddressSpecHash getOldestUnused(WalletDatabase db, NetworkParams params)
  {
    TreeMap<String, Long> unused = new TreeMap<>();
    TreeMap<String, AddressSpecHash> addr_to_hash_map = new TreeMap<>();

    for(AddressSpec spec : db.getAddressesList())
    {
      String addr = AddressUtil.getAddressString(spec, params);
      long tm = 0;
      if (db.getAddressCreateTimeMap().containsKey(addr))
      {
        tm = db.getAddressCreateTimeMap().get(addr);
      }
      addr_to_hash_map.put(addr, AddressUtil.getHashForSpec(spec));

      unused.put(addr, tm);
    }
    for(String addr : db.getUsedAddressesMap().keySet())
    {
      unused.remove(addr);
    }
    TreeMultimap<Long, String> age_map = TreeMultimap.create();
    for(Map.Entry<String, Long> me : unused.entrySet())
    {
      age_map.put(me.getValue(), me.getKey());
    }
    for(Map.Entry<Long, String> me : age_map.entries())
    {
      return addr_to_hash_map.get(me.getValue());
    }
    return null;
  }

  public static int getUnusedAddressCount(WalletDatabase db, NetworkParams params)
  {
    HashSet<String> unused=new HashSet<>();
    for(AddressSpec spec : db.getAddressesList())
    {
      String addr = AddressUtil.getAddressString(spec, params);
      unused.add(addr);
    }
    unused.removeAll(db.getUsedAddressesMap().keySet());


    return unused.size();
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
    byte[] rnd_data = new byte[7];
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

    logger.log(Level.FINE, String.format("Save to file %s completed", db_file.getPath()));
  }
}
