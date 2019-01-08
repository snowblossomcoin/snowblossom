package client.test;

import org.junit.Assert;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.Rule;

import snowblossom.lib.Globals;
import org.junit.rules.TemporaryFolder;

import snowblossom.proto.*;
import snowblossom.client.WalletUtil;

import java.io.File;
import snowblossom.lib.*;
import java.util.TreeMap;
import duckutil.ConfigMem;

import snowblossom.client.Purse;
import java.util.HashSet;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Callable;
import duckutil.TaskMaster;
import java.util.ArrayList;


public class PurseTest
{
  @Rule
  public TemporaryFolder test_folder = new TemporaryFolder();


  @BeforeClass
  public static void loadProvider()
  {
    Globals.addCryptoProvider();
  }

  @Test
  public void loadEmpty()
    throws Exception
  {
    File empty_sub = new File(test_folder.newFolder(), "empty");
    WalletDatabase db = WalletUtil.loadWallet(empty_sub, true, new NetworkParamsRegtest());
    Assert.assertNull(db);

  }

  @Test
  public void saveNew()
    throws Exception
  {
    File empty_sub = new File(test_folder.newFolder(), "new");

    TreeMap<String, String> config_settings = new TreeMap<>();
    config_settings.put("key_pool_size", "19");
    config_settings.put("key_mode", "standard");
    ConfigMem config = new ConfigMem(config_settings);

    NetworkParams params = new NetworkParamsRegtest();

    WalletDatabase db = WalletUtil.makeNewDatabase(config, params);

    db = WalletUtil.fillKeyPool(db, empty_sub, config, params);

    Assert.assertEquals(19, db.getKeysCount());
    Assert.assertEquals(19, db.getAddressesCount());
    Assert.assertEquals(19, db.getAddressCreateTimeCount());
    Assert.assertEquals(0, db.getUsedAddressesCount());
  }

  @Test
  public void testMarkRace() throws Exception
  {
    File empty_sub = new File(test_folder.newFolder(), "new");

    TreeMap<String, String> config_settings = new TreeMap<>();
    config_settings.put("key_pool_size", "100");
    ConfigMem config = new ConfigMem(config_settings);

    NetworkParams params = new NetworkParamsRegtest();

    WalletDatabase db = WalletUtil.makeNewDatabase(config, params);

    db = WalletUtil.fillKeyPool(db, empty_sub, config, params);

    Purse purse = new Purse(null, empty_sub, config, params);
    purse.maintainKeys(false);
    ThreadPoolExecutor exec = TaskMaster.getBasicExecutor(100, "test_fresh_race");

    TaskMaster<AddressSpecHash> tm = new TaskMaster<>(exec);

    for(int i=0; i<1000; i++)
    {
      tm.addTask(new Callable(){
        public AddressSpecHash call()
          throws Exception
        {
          return purse.getUnusedAddress(true, false);
        }
      });
    }

    ArrayList<AddressSpecHash> list = tm.getResults();

    Assert.assertEquals(1000, list.size());

    HashSet<AddressSpecHash> set = new HashSet<>();
    set.addAll(list);

    Assert.assertEquals(1000, set.size());
    Assert.assertEquals(1000, purse.getDB().getUsedAddressesCount());
    Assert.assertEquals(1000, purse.getDB().getAddressesCount());

    exec.shutdown();
  }

  @Test
  public void testMarkRaceGen() throws Exception
  {
    File empty_sub = new File(test_folder.newFolder(), "new");

    TreeMap<String, String> config_settings = new TreeMap<>();
    config_settings.put("key_pool_size", "100");
    config_settings.put("key_mode","standard");
    ConfigMem config = new ConfigMem(config_settings);

    NetworkParams params = new NetworkParamsRegtest();

    WalletDatabase db = WalletUtil.makeNewDatabase(config, params);

    db = WalletUtil.fillKeyPool(db, empty_sub, config, params);

    Purse purse = new Purse(null, empty_sub, config, params);
    purse.maintainKeys(false);
    ThreadPoolExecutor exec = TaskMaster.getBasicExecutor(100, "test_fresh_race");

    TaskMaster<AddressSpecHash> tm = new TaskMaster<>(exec);

    for(int i=0; i<1000; i++)
    {
      tm.addTask(new Callable(){
        public AddressSpecHash call()
          throws Exception
        {
          return purse.getUnusedAddress(true, true);
        }
      });
    }

    ArrayList<AddressSpecHash> list = tm.getResults();

    Assert.assertEquals(1000, list.size());

    HashSet<AddressSpecHash> set = new HashSet<>();
    set.addAll(list);

    Assert.assertEquals(1000, set.size());
    Assert.assertEquals(1000, purse.getDB().getUsedAddressesCount());
    Assert.assertEquals(1100, purse.getDB().getAddressesCount());

    exec.shutdown();
  }


}

