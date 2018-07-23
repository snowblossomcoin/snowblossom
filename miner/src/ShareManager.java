package snowblossom.miner;

import java.util.TreeMap;
import java.util.LinkedList;
import java.math.BigInteger;
import java.util.Map;
import com.google.protobuf.ByteString;
import com.google.common.collect.ImmutableMap;

import snowblossom.mining.proto.ShareEntry;
import snowblossom.mining.proto.PPLNSState;


public class ShareManager
{
  private long total_shares;
  private TreeMap<String, Long> share_map = new TreeMap<>();
  private LinkedList<ShareEntry> share_queue = new LinkedList<>();
  private ImmutableMap<String, Double> fixed_percentages;

  public ShareManager(Map<String, Double> fixed_percentages)
  {
    this(fixed_percentages, null);
  }
  public ShareManager(Map<String, Double> fixed_percentages, PPLNSState prev_state)
  {
    this.fixed_percentages = ImmutableMap.copyOf(fixed_percentages);

    if (prev_state != null)
    {
      for(ShareEntry e : prev_state.getShareEntriesList())
      {
        record(e.getAddress(), e.getShareCount());
      }
    }

  }

  public synchronized void record(String address, long shares)
  {
    share_queue.add( ShareEntry.newBuilder().setAddress(address).setShareCount(shares).build() );

    if (share_map.containsKey(address))
    {
      share_map.put(address, share_map.get(address) + shares);
    }
    else
    {
      share_map.put(address, shares);
    }
    total_shares += shares;
  }
  public synchronized void prune(long keep)
  {
    while(total_shares > keep)
    {
      ShareEntry e = share_queue.pollFirst();
      String address = e.getAddress();
      long map_shares = share_map.get(address);
      long shares = e.getShareCount();
      map_shares -= shares;

      if (map_shares == 0)
      {
        share_map.remove(address);
      }
      else
      {
        share_map.put(address, map_shares);
      }
      total_shares -= shares;
    }
  }
  public synchronized PPLNSState getState()
  {
    return PPLNSState.newBuilder().addAllShareEntries(share_queue).build();
  }

  public synchronized Map<String, Long> getShareMap()
  {
    return ImmutableMap.copyOf(share_map);
  }

  public synchronized Map<String, Double> getPayRatios()
  {
    TreeMap<String, Double> ratio_map = new TreeMap<>();
    for(Map.Entry<String, Long> me : share_map.entrySet())
    {
      double r = me.getValue();
      ratio_map.put(me.getKey(), r);
    }

    double total = total_shares;

    double total_fixed = 0.0;

    for(double d : fixed_percentages.values())
    {
      total_fixed += d;
    }

    double weight_fixed = 1.0;
    if (total_fixed == 0.0)
    {
      weight_fixed = 0.0;
    }
    else
    {
      if (total > 0.0)
      {
        weight_fixed = (total_fixed*total)/(1.0 - total_fixed);
        System.out.println("Total: " + total +  " Weight fixed: " + weight_fixed);
      }
    }

    for(Map.Entry<String, Double> me : fixed_percentages.entrySet())
    {
      double w = weight_fixed * me.getValue() / total_fixed;
      String address = me.getKey();
      if (ratio_map.containsKey(address))
      {
        ratio_map.put(address, ratio_map.get(address) + w);
      }
      else
      {
        ratio_map.put(address, w);
      }
    }

    return ratio_map;

  }

}
