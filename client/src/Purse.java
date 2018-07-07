package snowblossom.client;

import java.io.File;
import duckutil.Config;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.AddressSpecHash;
import snowblossom.proto.*;

import java.util.logging.Level;
import java.util.logging.Logger;



/** Where you keep your wallet i
 *
 * Used to coordinate writes to not get into weirdo states
 */
public class Purse
{
  private static final Logger logger = Logger.getLogger("snowblossom.client");

  private final File wallet_path;
  private final Config config;
  private final NetworkParams params;

  private volatile WalletDatabase wallet_database;

  public Purse(File wallet_path, Config config, NetworkParams params)
    throws Exception
  {
    this.wallet_path = wallet_path;
    this.config = config;
    this.params = params;

    wallet_database = WalletUtil.loadWallet(wallet_path, true);
    if (wallet_database == null)
    {
      logger.log(Level.WARNING, String.format("Directory %s does not contain wallet, creating new wallet", wallet_path.getPath()));
      wallet_database = WalletUtil.makeNewDatabase(config, params);
      WalletUtil.saveWallet(wallet_database, wallet_path);
    }

    wallet_database = WalletUtil.fillKeyPool(wallet_database, wallet_path, config, params);

  }

  public WalletDatabase getDB(){return wallet_database;}

	public synchronized void markUsed(AddressSpecHash hash)
    throws Exception
	{
    wallet_database = WalletUtil.markUsed(wallet_database, wallet_path, config, params, hash);
	}



}

