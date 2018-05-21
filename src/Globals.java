package snowblossom;

import java.security.Security;

public class Globals
{
  public static final String VERSION = "alpaca";

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

  /** Hash of the addres spec objects, represents recipent address */
  public static final String ADDRESS_SPEC_HASH_ALGO="Skein-256-160";
  public static final int ADDRESS_SPEC_HASH_LEN = 20;
    
  public static final int UTXO_KEY_LEN = ADDRESS_SPEC_HASH_LEN + BLOCKCHAIN_HASH_LEN + 2;

  public static final int MAX_OUTPUTS = 32768; // Using two bytes in utxo key len

  public static final int MAX_TX_EXTRA = 100;

  public static final long FLAKE_VALUE = 1L;
  public static final long SNOW_VALUE = 1000000L;
  public static final double SNOW_VALUE_D = SNOW_VALUE;

  public static final int MAX_BLOCK_SIZE = 3800000;
  public static final int MAX_TX_SIZE    = 1000000;


  public static void addCryptoProvider()
  {
    Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
  }



}
