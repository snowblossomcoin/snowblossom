package wallet.src;

import com.google.common.collect.ImmutableList;
import duckutil.Config;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.KeyUtil;
import snowblossom.proto.AddressSpec;
import snowblossom.proto.WalletDatabase;
import snowblossom.proto.WalletKeyPair;

import java.util.logging.Logger;

public class WalletUtil
{
  public static final String MODE_STANDARD = "standard";
  public static final String MODE_QHARD = "qhard";

  private static final Logger logger = Logger.getLogger("snowblossom.client");

  public static WalletDatabase makeNewDatabase(Config config)
  {
    WalletDatabase.Builder builder = WalletDatabase.newBuilder();

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
}