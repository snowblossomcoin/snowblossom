package snowblossom.client;

import java.util.TreeMap;
import java.util.TreeSet;

public class SeedReport
{
  public TreeMap<String, String> seeds;
  public TreeSet<String> watch_xpubs;

  public int missing_keys;

  public SeedReport()
  {
    seeds = new TreeMap<>();
    watch_xpubs = new TreeSet<>();
  }

}
