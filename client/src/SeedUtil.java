package snowblossom.client;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.ECPrivateKeySpec;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import org.bitcoinj.crypto.ChildNumber;
import org.bitcoinj.crypto.DeterministicHierarchy;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.MnemonicCode;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.DigestUtil;
import snowblossom.lib.Globals;
import snowblossom.lib.KeyUtil;
import snowblossom.lib.NetworkParams;
import snowblossom.lib.SignatureUtil;
import snowblossom.lib.ValidationException;
import snowblossom.proto.*;

public class SeedUtil
{

  public static MnemonicCode getMCode()
  {
    StringBuilder sb = new StringBuilder();
    for(String s : SeedWordList.getWordList())
    {
      sb.append(s); sb.append('\n');
    }
    byte[] b = sb.toString().getBytes();

    try
    {
      return new MnemonicCode(new ByteArrayInputStream(b),"ad90bf3beb7b0eb7e5acd74727dc0da96e0a280a258354e7293fb7e211ac03db");
    }
    catch(java.io.IOException e)
    {
      throw new RuntimeException(e);
    }
  }

  public static String generateSeed(int words)
  {
    Random rnd = new SecureRandom();
    int bc = 0;
    if (words == 12) bc=16;
    if (words == 18) bc=24;
    if (words == 24) bc=32;
    if (bc == 0) throw new RuntimeException("Words must be 12, 18 or 24");

    byte[] entropy = new byte[bc];
    rnd.nextBytes(entropy);

    try
    {
      List<String> lst = getMCode().toMnemonic(entropy);
      StringBuilder sb = new StringBuilder();
      boolean first=true;
      for(String s : lst)
      {
        if (!first) sb.append(" ");
        sb.append(s);
        first=false;
      }
      return sb.toString();
    }
    catch(org.bitcoinj.crypto.MnemonicException e)
    {
      throw new RuntimeException(e);
    }
  }

  public static ImmutableList<String> getWordsFromSeed(String str)
  {
    Scanner scan = new Scanner(str.toLowerCase());
    LinkedList<String> lst = new LinkedList<>();
    while(scan.hasNext())
    {
      lst.add(scan.next());
    }
    return ImmutableList.copyOf(lst);
  }

  public static void checkSeed(String seed)
    throws ValidationException
  {
    List<String> lst = getWordsFromSeed(seed);
    
    try
    {
      getMCode().check(lst);
    }
    catch(org.bitcoinj.crypto.MnemonicException e)
    {
      throw new ValidationException(e);
    }
  }

  public static ByteString decodeSeed(String seed, String pass)
  {
    List<String> lst = getWordsFromSeed(seed);
    return ByteString.copyFrom(getMCode().toSeed( lst, pass));
  }

  public static ByteString getSeedIdFromXpub(String xpub)
  {
    DeterministicKey account_key = DeterministicKey.deserializeB58(xpub, org.bitcoinj.params.MainNetParams.get());

    ByteString seed_id = ByteString.copyFrom(account_key.getIdentifier());

    return seed_id;
  }


  public static ByteString getSeedId(NetworkParams params, String seed_str, String pass, int account)
  {
    ByteString seed = decodeSeed(seed_str,pass);
    DeterministicKey dk = HDKeyDerivation.createMasterPrivateKey(seed.toByteArray());
    DeterministicHierarchy dh = new DeterministicHierarchy(dk);


    DeterministicKey dk_acct = dh.get( ImmutableList.of(
        new ChildNumber(44,true),
        new ChildNumber(params.getBIP44CoinNumber(),true),
        new ChildNumber(account,true)
      ),
      true, true);

    String xpub = dk_acct.serializePubB58( org.bitcoinj.params.MainNetParams.get() );
    ByteString seed_id = ByteString.copyFrom(dk_acct.getIdentifier());

    return seed_id;
  }

  public static String getSeedXpub(NetworkParams params, String seed_str, String pass, int account)
  {
    ByteString seed = decodeSeed(seed_str,pass);
    DeterministicKey dk = HDKeyDerivation.createMasterPrivateKey(seed.toByteArray());
    DeterministicHierarchy dh = new DeterministicHierarchy(dk);


    DeterministicKey dk_acct = dh.get( ImmutableList.of(
        new ChildNumber(44,true),
        new ChildNumber(params.getBIP44CoinNumber(),true),
        new ChildNumber(account,true)
      ),
      true, true);

    String xpub = dk_acct.serializePubB58( org.bitcoinj.params.MainNetParams.get() );
    return xpub;
  }

  public static AddressSpec getAddressSpec(NetworkParams params, String xpub, int change, int index)
  {
    DeterministicKey account_key = DeterministicKey.deserializeB58(xpub, org.bitcoinj.params.MainNetParams.get());

    DeterministicHierarchy dh = new DeterministicHierarchy(account_key);

    DeterministicKey dk_addr = dh.get( ImmutableList.of(
      new ChildNumber(change,false),
      new ChildNumber(index,false)
    ), true, true);


    ByteString public_key = ByteString.copyFrom(dk_addr.getPubKey());

    AddressSpec addr_spec = AddressUtil.getSimpleSpecForKey(public_key, SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);

    return addr_spec;

  }

  public static WalletKeyPair getKeyFromDeterministicKey(DeterministicKey dk_addr, ByteString seed_id, int change, int index)
  {
    ByteString priv_key = ByteString.copyFrom(dk_addr.getPrivKeyBytes());
    BigInteger priv_bi = dk_addr.getPrivKey();

    PrivateKey pk = null;

    try
    {

      ECPrivateKeySpec priv_spec = new ECPrivateKeySpec(priv_bi, KeyUtil.getECHDSpec());
      KeyFactory kf = KeyFactory.getInstance("ECDSA", Globals.getCryptoProviderName());
      pk = kf.generatePrivate(priv_spec);

    }
    catch(Exception e){throw new RuntimeException(e);}
    
    ByteString public_key = ByteString.copyFrom(dk_addr.getPubKey());
    return WalletKeyPair.newBuilder()
      .setPublicKey(public_key)
      .setPrivateKey(ByteString.copyFrom(pk.getEncoded()))
      .setSignatureType(SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED)
      .setSeedId(seed_id)
      .setHdPath(dk_addr.getPathAsString())
      .setHdChange(change)
      .setHdIndex(index)
      .build();

  }

  public static WalletKeyPair getKey(NetworkParams params, String seed_str, String pass, int account, int change, int index)
  {
    ByteString seed = decodeSeed(seed_str,pass);
    DeterministicKey dk = HDKeyDerivation.createMasterPrivateKey(seed.toByteArray());
    DeterministicHierarchy dh = new DeterministicHierarchy(dk);

    ByteString seed_id = getSeedId(params, seed_str, pass, 0);


    DeterministicKey dk_addr = dh.get( ImmutableList.of(
        new ChildNumber(44,true),
        new ChildNumber(params.getBIP44CoinNumber(),true),
        new ChildNumber(account,true),
        new ChildNumber(change,false),
        new ChildNumber(index,false)
      ),
      true, true);
    
    return getKeyFromDeterministicKey(dk_addr, seed_id, change, index);

  }

  /** Generate a deterministic key for the given string site for the given seed */
  public static WalletKeyPair getSiteKey(NetworkParams params, String seed_str, String pass, String site)
  {
    ByteString seed = decodeSeed(seed_str,pass);
    DeterministicKey dk = HDKeyDerivation.createMasterPrivateKey(seed.toByteArray());
    DeterministicHierarchy dh = new DeterministicHierarchy(dk);

    ByteString seed_id = getSeedId(params, seed_str, pass, 0);

    LinkedList<ChildNumber> hd_path = new LinkedList<>();

    hd_path.add(new ChildNumber(Globals.HD_SITE_PURPOSE,true));
    hd_path.add(new ChildNumber(params.getBIP44CoinNumber(),true));


    // We take the site string, hash it
    // and then use that data as ints in the HD
    // we use the abs, since they shouldn't be negative
    // and if they are negative max int, use zero instead

    // Result is terrible paths like:
    // M/981231H/2339H/671271860H/672827692H/1806601323H/705147759H/626028654H/1663169488H/712115807H/1149046820H
    ByteString site_hash = DigestUtil.hash(ByteString.copyFrom(site.getBytes()));


    ByteBuffer bb = site_hash.asReadOnlyByteBuffer();
    while(bb.remaining() >= 4)
    {
      int v = bb.getInt();
      v = Math.abs(v);
      if (v < 0) v = 0;

      hd_path.add(new ChildNumber(v, true));
    }

    DeterministicKey dk_addr = dh.get( hd_path, true, true);
    
    return getKeyFromDeterministicKey(dk_addr, seed_id, 0, 0);

  }
  

}
