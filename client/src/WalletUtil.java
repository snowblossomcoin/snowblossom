package snowblossom.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.TreeMultimap;
import com.google.protobuf.ByteString;
import duckutil.AtomicFileOutputStream;
import duckutil.Config;
import duckutil.ConfigMem;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.ChainHash;
import snowblossom.lib.HexUtil;
import snowblossom.lib.KeyUtil;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.SignatureUtil;
import snowblossom.lib.UpClock;
import snowblossom.lib.ValidationException;
import snowblossom.proto.*;

public class WalletUtil
{
  
  public static final String MODE_STANDARD = "standard";
  public static final String MODE_QHARD = "qhard";
  public static final String MODE_PQC1 = "pqc1";
  public static final String MODE_SEED = "seed";

  private static final Logger logger = Logger.getLogger("snowblossom.client");

  /** Version log
   * 0 - old wallet.db version
   * 2 - merging support, multiple files
   * 3 - network version added
   * 4 - added seeds
   * 5 - added mpk
   */
  public static final int WALLET_DB_VERSION = 5;

  public static WalletDatabase makeNewDatabase(Config config, NetworkParams params)
  {
    return makeNewDatabase(config, params, null);
  }
  public static WalletDatabase makeNewDatabase(Config config, NetworkParams params, String import_seed)
  {
    WalletDatabase.Builder builder = WalletDatabase.newBuilder();
    builder.setVersion(WALLET_DB_VERSION);
    builder.setNetwork(params.getNetworkName());

    if (!config.getBoolean("watch_only"))
    {
      if (import_seed != null)
      {
         ByteString seed_id = SeedUtil.getSeedId(params, import_seed, "", 0);
         String seed_xpub = SeedUtil.getSeedXpub(params, import_seed, "", 0);
        builder.putSeeds(import_seed, SeedStatus.newBuilder().setSeedId(seed_id).setSeedXpub(seed_xpub).build());
      }
      else
      {
        int count = config.getIntWithDefault("key_count", 8);
        for (int i = 0; i < count; i++)
        {
          genNewKey(WalletDatabase.newBuilder().build(), builder, config, params);
        }
      }
    }
    else
    {
      if (import_seed != null)
      {
        throw new RuntimeException("Can only import xpub into watch_only wallet");

      }

    }

    return builder.build();
  }

  public static WalletDatabase importXpub(NetworkParams params, String xpub)
  {

    WalletDatabase.Builder builder = WalletDatabase.newBuilder();
    builder.setVersion(WALLET_DB_VERSION);
    builder.setNetwork(params.getNetworkName());
    ByteString seed_id = SeedUtil.getSeedIdFromXpub(xpub);
    builder.putXpubs(xpub, SeedStatus.newBuilder().setSeedId(seed_id).setSeedXpub(xpub).build());

    return builder.build();
  }

  public static void genNewKey(WalletDatabase existing_wallet, WalletDatabase.Builder wallet_builder, Config config, NetworkParams params)
  {
    genNewKey(existing_wallet, wallet_builder, config, params, null);
  }

  public static void genNewKey(WalletDatabase existing_wallet, WalletDatabase.Builder wallet_builder, Config config, NetworkParams params, String gen_seed)
  {
    if (config.getBoolean("watch_only"))
    {
      throw new RuntimeException("Unable to create new address on watch only wallet.");
    }
    String key_mode = config.getWithDefault("key_mode", MODE_SEED).toLowerCase();
    AddressSpec claim = null;

    if ((key_mode.equals(MODE_SEED)) || (gen_seed != null))
    {
      existing_wallet = mergeDatabases(ImmutableList.of(existing_wallet, wallet_builder.build()), params);
      int next_index=0;
      if (gen_seed == null)
      {
        if (existing_wallet.getSeedsCount() == 0)
        {
          logger.info("Generating new seed");
          String seed_str = SeedUtil.generateSeed(12);
          ByteString seed_id = SeedUtil.getSeedId(params, seed_str, "", 0);
          String seed_xpub = SeedUtil.getSeedXpub(params, seed_str, "", 0);

          wallet_builder.putSeeds(seed_str, 
            SeedStatus.newBuilder()
              .setSeedId(seed_id)
              .setSeedXpub(seed_xpub)
              .putAddressIndex(0, next_index)
              .build());
          gen_seed = seed_str;
        }
        else
        {
          gen_seed = existing_wallet.getSeedsMap().keySet().iterator().next();
          next_index = existing_wallet.getSeedsMap().get(gen_seed).getAddressIndexOrDefault(0,-1) + 1;
          ByteString seed_id = existing_wallet.getSeedsMap().get(gen_seed).getSeedId();
          String seed_xpub = existing_wallet.getSeedsMap().get(gen_seed).getSeedXpub();
          if ((seed_xpub == null) || (seed_xpub.length() == 0))
          {
            seed_xpub = SeedUtil.getSeedXpub(params, gen_seed, "", 0);
          }

          wallet_builder.putSeeds(gen_seed, 
            SeedStatus.newBuilder()
              .setSeedId(seed_id)
              .setSeedXpub(seed_xpub)
              .putAddressIndex(0, next_index)
              .build());
        }
      }
      else
      {
        next_index = existing_wallet.getSeedsMap().get(gen_seed).getAddressIndexOrDefault(0,-1) + 1;
        ByteString seed_id = existing_wallet.getSeedsMap().get(gen_seed).getSeedId();
        String seed_xpub = existing_wallet.getSeedsMap().get(gen_seed).getSeedXpub();
        if ((seed_xpub == null) || (seed_xpub.length() == 0))
        {
          seed_xpub = SeedUtil.getSeedXpub(params, gen_seed, "", 0);
        }

        wallet_builder.putSeeds(gen_seed, 
          SeedStatus.newBuilder()
              .setSeedId(seed_id)
              .setSeedXpub(seed_xpub)
              .putAddressIndex(0, next_index)
              .build());

      }
      WalletKeyPair wkp = SeedUtil.getKey(params, gen_seed, "", 0, 0, next_index);
      wallet_builder.addKeys(wkp);
      claim = AddressUtil.getSimpleSpecForKey(wkp);
      wallet_builder.addAddresses(claim);
      
    }
    else if (key_mode.equals(MODE_STANDARD))
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
    else if (key_mode.equals(MODE_PQC1))
    {
      logger.info("Creating PQC1 key set. This takes a while.");
      WalletKeyPair k_ec = KeyUtil.generateWalletStandardECKey();
      WalletKeyPair k_dstu = KeyUtil.generateWalletDSTU4145Key(9);
      WalletKeyPair k_dilithium = KeyUtil.generateWalletDilithiumKey();
      WalletKeyPair k_sphincs = KeyUtil.generateWalletSphincsPlusKey();

      wallet_builder.addKeys(k_ec);
      wallet_builder.addKeys(k_dstu);
      wallet_builder.addKeys(k_dilithium);
      wallet_builder.addKeys(k_sphincs);

      claim = AddressUtil.getMultiSig(4, ImmutableList.of(k_ec, k_dstu, k_dilithium, k_sphincs));
      wallet_builder.addAddresses(claim);
      
    }

    else
    {
      throw new RuntimeException("Unknown key_mode: " + key_mode);
    }

    wallet_builder.putAddressCreateTime( AddressUtil.getAddressString(claim, params), UpClock.time());
  }

  public static WalletDatabase fillKeyPool(WalletDatabase existing_db, File wallet_path, Config config, NetworkParams params)
    throws Exception
  {
    WalletDatabase.Builder partial_new_db = WalletDatabase.newBuilder();
    partial_new_db.setVersion(WALLET_DB_VERSION);
    partial_new_db.setNetwork(params.getNetworkName());
    boolean save=false;
    
    if (config.getBoolean("watch_only"))
    {
      if (existing_db.getXpubsCount() > 0)
      {
        if (addXpubGapAddresses(params, config, existing_db, partial_new_db))
        {
          save=true;
        }
      }
    }
    else
    {
      int key_pool = config.getIntWithDefault("key_pool_size", 10);
      int unused = getUnusedAddressCount(existing_db, params);
      //if (unused < key_pool)
      {
        int to_make = key_pool - unused;
        for(int i=0; i<to_make; i++)
        {
          genNewKey(existing_db, partial_new_db, config, params);
          save=true;
        }
        if (addSeedGapKeys(params, config, existing_db, partial_new_db))
        {
          save=true;
        }
      }
    }

    if (save)
    {
      WalletDatabase new_db_part = partial_new_db.build();
      saveWallet(new_db_part, wallet_path);

      return mergeDatabases(ImmutableList.of(existing_db, new_db_part), params);
    }

    return existing_db;
  }

  private static boolean addSeedGapKeys(NetworkParams params, Config config, WalletDatabase existing_db, WalletDatabase.Builder partial_new_db)
  {
    int gap = config.getIntWithDefault("seed_gap", 20);
    
    existing_db = mergeDatabases(ImmutableList.of(existing_db, partial_new_db.build()), params);
    boolean added=false;

    for(String seed : existing_db.getSeedsMap().keySet())
    {
      ByteString seed_id = existing_db.getSeedsMap().get(seed).getSeedId();
      int max_used = 0;
      for(WalletKeyPair wkp : existing_db.getKeysList())
      {
        if (wkp.getSeedId().equals(seed_id))
        {
          AddressSpec claim = AddressUtil.getSimpleSpecForKey(wkp);
          
          String addr = AddressUtil.getAddressString(claim, params);
          if (existing_db.getUsedAddressesMap().containsKey(addr))
          {
            max_used = Math.max(max_used, wkp.getHdIndex());
          }
        }
      }
      int current_idx = existing_db.getSeedsMap().get(seed).getAddressIndexOrDefault(0,0);
      //logger.info(String.format("Seed %d %d %d", max_used, gap, current_idx));
      if (max_used + gap > current_idx)
      {
        for(int i = current_idx; i< max_used + gap; i++)
        {
          //logger.info(String.format("Making key for seed %s %d", HexUtil.getHexString(seed_id), i));
          genNewKey(existing_db, partial_new_db, config, params, seed);
          added=true;
        }
      }
    }
    return added;
    
  }
  private static boolean addXpubGapAddresses(NetworkParams params, Config config, WalletDatabase existing_db, WalletDatabase.Builder partial_new_db)
  {
    int gap = config.getIntWithDefault("seed_gap", 20);
    
    existing_db = mergeDatabases(ImmutableList.of(existing_db, partial_new_db.build()), params);
    boolean added=false;

    for(String xpub : existing_db.getXpubsMap().keySet())
    {
      ByteString seed_id = existing_db.getXpubsMap().get(xpub).getSeedId();
      int max_used=-1;

      for(int i=0; i<= max_used + gap; i++)
      {
        AddressSpec claim = SeedUtil.getAddressSpec(params, xpub, 0, i);
        AddressSpecHash address_hash = AddressUtil.getHashForSpec(claim);
        String addr = address_hash.toAddressString(params);

        if (existing_db.getUsedAddressesMap().containsKey(addr))
        {
          max_used = Math.max(max_used, i);
        }
        if (!existing_db.getAddressCreateTimeMap().containsKey(addr))
        {
          partial_new_db.addAddresses(claim);
          partial_new_db.putAddressCreateTime(addr, UpClock.time());
          added=true;
        }
      }
      
      partial_new_db.putXpubs(xpub, 
          SeedStatus.newBuilder()
              .setSeedId(seed_id)
              .setSeedXpub(xpub)
              .putAddressIndex(0, max_used+gap+1)
              .build());
    }
    return added;
    
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
    partial_new_db.setNetwork(params.getNetworkName());
    partial_new_db.putUsedAddresses(address, true);

    WalletDatabase new_db_part = partial_new_db.build();
    saveWallet(new_db_part, wallet_path);

    return mergeDatabases(ImmutableList.of(existing_db, new_db_part),params);

  }

  public static Collection<AddressSpecHash> getAddressesByAge(WalletDatabase db, NetworkParams params)
  {
    TreeMultimap<Long, AddressSpecHash> age_map = TreeMultimap.create();
    for(AddressSpec spec : db.getAddressesList())
    {
      String addr = AddressUtil.getAddressString(spec, params);
      long tm = 0;
      if (db.getAddressCreateTimeMap().containsKey(addr))
      {
        tm = db.getAddressCreateTimeMap().get(addr);
      }
      age_map.put(tm, AddressUtil.getHashForSpec(spec));

    }

    return age_map.values();

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
  public static Collection<AddressSpecHash> getAllUnused(WalletDatabase db, NetworkParams params)
  {
    TreeMap<String, AddressSpecHash> addr_to_hash_map = new TreeMap<>();

    for(AddressSpec spec : db.getAddressesList())
    {
      String addr = AddressUtil.getAddressString(spec, params);
      addr_to_hash_map.put(addr, AddressUtil.getHashForSpec(spec));
    }
    for(String addr : db.getUsedAddressesMap().keySet())
    {
      addr_to_hash_map.remove(addr);
    }

    return addr_to_hash_map.values();

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

  public static WalletDatabase loadWallet(File wallet_path, boolean cleanup, NetworkParams params)
    throws Exception
  {
    String network_name = params.getNetworkName();

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

      if (w.getNetwork().length() > 0)
      {
        if (!w.getNetwork().equals( params.getNetworkName() ))
        {
          throw new Exception(String.format("Wallet load error: in file %s, attempting to load network %s, expecting %s",f.toString(), w.getNetwork(), params.getNetworkName()));
        }
      }

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
      WalletDatabase merged = mergeDatabases(found_db_list,params);

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

  public static WalletDatabase mergeDatabases(List<WalletDatabase> src_list, NetworkParams params)
  {
    WalletDatabase.Builder new_db = WalletDatabase.newBuilder();

    new_db.setVersion(WALLET_DB_VERSION);
    new_db.setNetwork(params.getNetworkName());

    HashSet<WalletKeyPair> key_set=new HashSet<>();
    HashSet<AddressSpec> address_set =new HashSet<>();
    HashSet<Transaction> tx_set = new HashSet<>();
    HashMap<String, Boolean> used_addresses = new HashMap<>();
    HashMap<String, Long> address_create_time = new HashMap<>();
    HashMap<String, SeedStatus> seed_map = new HashMap<>();
    HashMap<String, SeedStatus> xpub_map = new HashMap<>();

    for(WalletDatabase src : src_list)
    { 
      key_set.addAll(src.getKeysList());
      address_set.addAll(src.getAddressesList());
      tx_set.addAll(src.getTransactionsList());

      used_addresses.putAll(src.getUsedAddressesMap());
      address_create_time.putAll(src.getAddressCreateTimeMap());

      for(String seed : src.getSeedsMap().keySet())
      {
        SeedStatus status = src.getSeedsMap().get(seed);
        if (seed_map.containsKey(seed))
        {
          seed_map.put( seed, mergeSeedStatus(status, seed_map.get(seed) ));
        }
        else
        {
          seed_map.put(seed, status);
        }
      }
      for(String xpub : src.getXpubsMap().keySet())
      {
        SeedStatus status = src.getXpubsMap().get(xpub);
        if (xpub_map.containsKey(xpub))
        {
          xpub_map.put(xpub, mergeSeedStatus(status, xpub_map.get(xpub) ));
        }
        else
        {
          xpub_map.put(xpub, status);
        }
      }
 
    }

    new_db.addAllKeys(key_set);
    new_db.addAllAddresses(address_set);
    new_db.addAllTransactions(tx_set);
    new_db.putAllUsedAddresses(used_addresses);
    new_db.putAllAddressCreateTime(address_create_time);
    new_db.putAllSeeds(seed_map);
    new_db.putAllXpubs(xpub_map);

    return new_db.build();
  }

  public static SeedStatus mergeSeedStatus(SeedStatus a, SeedStatus b)
  {
    if (!a.getSeedId().equals(b.getSeedId())) throw new RuntimeException("Attempt to merge SeedStatus for different seed ids");

    SeedStatus.Builder new_seed = SeedStatus.newBuilder();
    new_seed.setSeedId(a.getSeedId());

    if ((a.getSeedXpub() != null) && (a.getSeedXpub().length() > 0)) new_seed.setSeedXpub(a.getSeedXpub());
    else new_seed.setSeedXpub(b.getSeedXpub());

    HashSet<Integer> change_groups = new HashSet<Integer>();
    change_groups.addAll( a.getAddressIndexMap().keySet());
    change_groups.addAll( b.getAddressIndexMap().keySet());

    for(int i : change_groups)
    {
      int v = Math.max( 
        a.getAddressIndexOrDefault(i, 0),
        b.getAddressIndexOrDefault(i, 0)
        );
      new_seed.putAddressIndex(i, v);
    }

    return new_seed.build();

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


    { // Write readme file
      File readme_file = new File(wallet_path, "readme.txt");
      FileOutputStream readme_out = new FileOutputStream(readme_file);

      PrintStream readme_print = new PrintStream(readme_out);

      readme_print.println("This directory contains a Snowblossom Wallet.");
      readme_print.println("");
      readme_print.println("If backing this up, save the entire directory including all .wallet files in it.");
      readme_print.println("It is safe to access a wallet directory from multiple clients on the same computer");
      readme_print.println("or on multiple computers with real-time or eventual synchronization.");
      readme_print.println("");
      readme_print.println("Wallet data safety is ensured by the snowblossom client always writing the wallet");
      readme_print.println("out to a new file with a random file name and only then deleting the old file(s).");
      readme_print.println("This way, if multiple clients are making changes there will simple be multiple files.");
      readme_print.println("The next client to update the wallet will merge those and make a new single file.");
      readme_print.println("");
      readme_print.println("In addition, each file has a proto version.  If a client sees a higher version it will");
      readme_print.println("assume there are new fields that it does not know how to correctly merge and won't remove");
      readme_print.println("the higher versioned files.  So it will always be safe to open a wallet with an older");
      readme_print.println("snowblossom client.");

      readme_print.flush();
      readme_out.close();

    

    }

    logger.log(Level.FINE, String.format("Save to file %s completed", db_file.getPath()));
  }

  public static WalletDatabase getWatchCopy(WalletDatabase db)
  {
    WalletDatabase.Builder watch = WalletDatabase.newBuilder();

    watch.mergeFrom(db);
    watch.clearKeys();
    watch.clearSeeds();

    return watch.build();
  }


  public static List<AddressSpecHash> testWallet(WalletDatabase db)
    throws ValidationException
  {
    Random rnd = new Random();
    byte[] rnd_bytes = new byte[32];
    rnd.nextBytes(rnd_bytes);
    ChainHash rnd_hash = new ChainHash(rnd_bytes);

    for(WalletKeyPair pair : db.getKeysList())
    {
      SigSpec sig_spec = SigSpec.newBuilder()
        .setSignatureType( pair.getSignatureType() )
        .setPublicKey( pair.getPublicKey() )
        .build();

      ByteString sig = SignatureUtil.sign(pair, rnd_hash);

      if (!SignatureUtil.checkSignature(sig_spec, rnd_hash.getBytes(), sig))
      {
        throw new ValidationException("Signature check failure on keypair: " + pair);
      }
    }

    LinkedList<AddressSpecHash> addresses = new LinkedList<>();

    for(AddressSpec spec : db.getAddressesList())
    {
      addresses.add( AddressUtil.getHashForSpec(spec) );
    }
    return addresses;
  }

  public static SeedReport getSeedReport(WalletDatabase db)
  {
    SeedReport sr = new SeedReport();

    HashSet<ByteString> seed_ids = new HashSet<>();
    HashSet<String> xpubs_with_seeds = new HashSet<>();

    for(String seed : db.getSeedsMap().keySet())
    {
      String xpub=db.getSeedsMap().get(seed).getSeedXpub();
      sr.seeds.put(seed, xpub);
      seed_ids.add(db.getSeedsMap().get(seed).getSeedId());

      xpubs_with_seeds.add(xpub);
    }
    for(String xpub : db.getXpubsMap().keySet())
    {
      if (!xpubs_with_seeds.contains(xpub))
      {
        sr.watch_xpubs.add(xpub);
      }
    }


    sr.missing_keys = 0;
    for(WalletKeyPair wkp : db.getKeysList())
    {
      ByteString id = wkp.getSeedId();
      if (!seed_ids.contains(id)) sr.missing_keys++;
    }

    return sr;
  }


  public static void printBasicStats(WalletDatabase db)
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

  public static boolean isXpub(String s)
  {
    return (s.startsWith("xpub"));
  }


  /**
   * Loads or creates a single key wallet based on the config param name pointing
   * to a path.
   */
  public static WalletDatabase loadNodeWalletFromConfig(NetworkParams params, Config config, String param_name)
    throws Exception
  {
    if (!config.isSet(param_name)) return null;

    config.require(param_name);

    TreeMap<String, String> wallet_config_map = new TreeMap<>();
    wallet_config_map.put("wallet_path", config.get(param_name));
    wallet_config_map.put("key_count", "1");
    wallet_config_map.put("key_mode", WalletUtil.MODE_STANDARD);
    ConfigMem config_wallet = new ConfigMem(wallet_config_map);
    File wallet_path = new File(config_wallet.get("wallet_path"));

    WalletDatabase wallet_db = WalletUtil.loadWallet(wallet_path, true, params);
    if (wallet_db == null)
    {
      logger.log(Level.WARNING, String.format("Directory %s does not contain keys, creating new keys", wallet_path.getPath()));
      wallet_db = WalletUtil.makeNewDatabase(config_wallet, params);
      WalletUtil.saveWallet(wallet_db, wallet_path);

      AddressSpecHash spec = AddressUtil.getHashForSpec(wallet_db.getAddresses(0));
      String addr = AddressUtil.getAddressString("node", spec);

      File dir = new File(config.get(param_name));
      PrintStream out = new PrintStream(new FileOutputStream( new File(dir, "address.txt"), false));
      out.println(addr);
      out.close();

    }

    return wallet_db;

  }

}
