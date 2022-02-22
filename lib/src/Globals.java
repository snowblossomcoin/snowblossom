package snowblossom.lib;

import java.security.Security;

public class Globals
{
  public static final String VERSION = "2.0.0-release";

  public static final int POW_LOOK_PASSES = 6;

  /** Used for the merkle proofs of data in snow files */
  public static final String SNOW_MERKLE_HASH_ALGO="Skein-256-128";
  public static final int SNOW_MERKLE_HASH_LEN=16;
  
  /** Used for hashes in block chain stuff, utxo root, merkle root, block hashes, tx hashes */
  public static final String BLOCKCHAIN_HASH_ALGO="Skein-256-256";
  public static final int BLOCKCHAIN_HASH_LEN=32;

  public static final int NONCE_LENGTH = 12;
  public static final int TARGET_LENGTH = 32;

  public static final int COINBASE_REMARKS_MAX=100;

  public static final int MAX_PREVIEW_CHAIN_LENGTH = 100;

  /** Hash of the addres spec objects, represents recipent address */
  public static final String ADDRESS_SPEC_HASH_ALGO="Skein-256-160";
  public static final int ADDRESS_SPEC_HASH_LEN = 20;
    
  public static final int UTXO_KEY_LEN = ADDRESS_SPEC_HASH_LEN + BLOCKCHAIN_HASH_LEN + 2;

  public static final int MAX_OUTPUTS = 32768; // Using two bytes in utxo key len

  public static final int MAX_TX_EXTRA = 100;

  public static final long FLAKE_VALUE = 1L;
  public static final long SNOW_VALUE = 1000000L;
  public static final double SNOW_VALUE_D = SNOW_VALUE;



  public static final int MAX_TX_SIZE    =       1000000;
  public static final int LOW_FEE_SIZE_IN_BLOCK = 100000;

  public static final int MAX_NODE_ID_SIZE = 8;

  public static final long CLOCK_SKEW_WARN_MS = 5000;

  public static final int BLOCK_CHUNK_HEADER_DOWNLOAD_SIZE = 500;

  public static final int ADDRESS_HISTORY_MAX_REPLY = 10000;

  public static final long MINE_CHUNK_SIZE = 1024L*1024L*1024L;

  public static final String NODE_ADDRESS_STRING = "node";

  // In flakes per byte
  public static final double BASIC_FEE = 2.5;
  public static final double LOW_FEE = 2.2;


  // Site key HD derivation purpose
  // See bip32, 43 and 44.
  // Short story, int 44 is used for crypytocurrency
  // So we want a different one used for site auth
  public static final int HD_SITE_PURPOSE = 981231; 


  // Not a big fan of static global variables
  // but already loading the cryptoprovider is a global
  // thing that effects state so this isn't far from
  // that.
  private static String crypt_provider_name;
  public static String getCryptoProviderName()
  {
    return crypt_provider_name;
  }

  public static void addCryptoProvider()
  {
    Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    crypt_provider_name = "BC";
  }

}
