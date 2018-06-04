package snowblossom.lib;

import com.google.protobuf.ByteString;
import snowblossom.lib.trie.HashUtils;

import java.io.*;
import java.security.MessageDigest;
import java.security.Security;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.text.DecimalFormat;


public class SnowMerkle
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  public static void main(String args[]) throws Exception
  {
    Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

    if (args.length == 2)
    {
      System.out.println(new SnowMerkle(new File(args[0]), args[1], true).getRootHashStr());
    }
    else
    {
      System.out.println("SnowMerkle <path> <base_name>");
      System.exit(-1);
    }

  }

  public static final int HASH_LEN = Globals.SNOW_MERKLE_HASH_LEN;
  public static final long HASH_LEN_LONG = HASH_LEN;

  /** Means 1k items under each deck entry, meaning
   * 1k * 16 = 16k reads needed for PoW Merkle proof generation */
  public static final long DECK_ENTIRES = 1024;


  private MessageDigest md;

  private DataInputStream buffin;
  private byte[] root_hash;

  private TreeMap<Long, OutputStream> deck_map;
  private long blocks;

  public SnowMerkle(File path, String base, boolean make_decks)
    throws Exception
  {
    File input = new File(path, base + ".snow");

    buffin = new DataInputStream(
      new BufferedInputStream(
      new FileInputStream(input), 1048576));

    md = MessageDigest.getInstance(Globals.SNOW_MERKLE_HASH_ALGO);

    long total_len = input.length();
    if (total_len % HASH_LEN_LONG != 0) throw new RuntimeException("Impedence mismatch - " + total_len);

    blocks = total_len / HASH_LEN_LONG;


    deck_map = new TreeMap<>();
    if (make_decks)
    {
      int deck_count = getNumberOfDecks(blocks);
      long h = 1;

      for(int i = 0; i<deck_count; i++)
      {
        h = h * DECK_ENTIRES;
        
        char letter = (char) ('a' + i);
        OutputStream out = 
            new BufferedOutputStream(
            new FileOutputStream(new File(path, base +".deck." + letter), false), 1048576);

        deck_map.put(h, out);
      }
    }

    root_hash = findTreeHash(0, blocks);


    buffin.close();

    for(OutputStream out : deck_map.values())
    {
      out.flush();
      out.close();
    }
  }

  public ByteString getRootHash()
  {
    return ByteString.copyFrom(root_hash);
  }
  public String getRootHashStr()
  {
    return HashUtils.getHexString(root_hash);

  }

  private byte[] findTreeHash(long start, long end)
    throws Exception
  {
    long dist = end - start;

    if (dist == 0) throw new RuntimeException("lol no");

    if (dist == 1)
    {
      byte[] buff = new byte[HASH_LEN];
      buffin.readFully(buff);
      return buff;
    }
    if (dist % 2 != 0) throw new RuntimeException("lolwut");


    long mid = (start + end ) / 2;

    byte[] left = findTreeHash(start, mid);
    byte[] right = findTreeHash(mid, end);

    md.update(left);
    md.update(right);

    byte[] hash = md.digest();

    if (deck_map.containsKey(dist))
    {
      deck_map.get(dist).write(hash);
      if (dist == DECK_ENTIRES * DECK_ENTIRES)
      {
        double percent = (double) end / (double) blocks;
        DecimalFormat df = new DecimalFormat("0.000");
        logger.info(String.format("SnowMerkle computation %s complete %d/%d", df.format(percent), end, blocks));
      }
    }

    return hash;

  }

  public static int getNumberOfDecks(long total_words)
  {
    int count = 0;
    long w = total_words;
    while(w > DECK_ENTIRES)
    {
      w = w / DECK_ENTIRES;
      count ++;
    }
    return count;

  }

}
