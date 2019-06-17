package snowblossom.client;

import java.util.TreeMap;

public class SeedReport
{
  TreeMap<String, String> seeds;
  int missing_keys;

  public SeedReport()
  {
    seeds = new TreeMap<>();
  }

}
