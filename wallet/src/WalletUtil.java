package wallet.src;

import com.google.common.collect.ImmutableList;
import duckutil.Config;
import org.junit.Assert;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.KeyUtil;
import snowblossom.proto.AddressSpec;
import snowblossom.proto.WalletDatabase;
import snowblossom.proto.WalletKeyPair;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WalletUtil
{
  public static final String MODE_STANDARD = "standard";
  public static final String MODE_QHARD = "qhard";

  private static final Logger logger = Logger.getLogger("snowblossom.client");

  public static WalletDatabase makeNewDatabase(int keyCount, String keyMode)
  {
    WalletDatabase.Builder builder = WalletDatabase.newBuilder();

    for (int i = 0; i < keyCount; i++)
    {
      genNewKey(builder, keyMode);
    }

    return builder.build();
  }

  public static void genNewKey(WalletDatabase.Builder wallet_builder, String key_mode)
  {

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

  public static void saveWallet(WalletDatabase wallet, File file) throws Exception
  {
    File db_file_tmp = new File(file.getCanonicalPath() + ".tmp");
    db_file_tmp.delete();

    FileOutputStream out = new FileOutputStream(db_file_tmp, false);
    wallet.writeTo(out);
    out.flush();
    out.close();

    FileInputStream re_read_in = new FileInputStream(db_file_tmp);
    WalletDatabase read = WalletDatabase.parseFrom(re_read_in);
    re_read_in.close();

    Assert.assertEquals(wallet, read);

    Path db_file_path = FileSystems.getDefault().getPath(file.getPath());
    Path tmp_file_path = FileSystems.getDefault().getPath(db_file_tmp.getPath());

    Files.move(tmp_file_path, db_file_path, StandardCopyOption.REPLACE_EXISTING);
    logger.log(Level.INFO, String.format("Save to file %s completed", file.getPath()));
  }

  public static WalletDatabase makeWallet(File file, int keyCount, String keyMode) throws Exception
  {
    file.mkdirs();
    logger.log(Level.WARNING, String.format("creating new wallet at [%s]", file.getPath()));
    WalletDatabase wallet = WalletUtil.makeNewDatabase(keyCount, keyMode);
    saveWallet(wallet, file);
    return wallet;
  }

  public static WalletDatabase loadWallet(File file) throws Exception
  {
    if (file.exists())
    {
      return WalletDatabase.parseFrom(new FileInputStream(file));
    }
    return null;
  }
}