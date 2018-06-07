package miner.test;

import org.junit.Test;
import org.junit.Assert;

import snowblossom.miner.ShareManager;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Map;

public class ShareManagerTest
{

  @Test
  public void testShareManagerEmpty()
  {
    TreeMap<String, Double> fixed = new TreeMap<>();
    fixed.put("pool", 0.01);
    ShareManager sm = new ShareManager(fixed);

    Map<String, Double> map = sm.getPayRatios();

    Assert.assertEquals(1, map.size());
    
    ArrayList<Double> values = new ArrayList<Double>();
    values.addAll(map.values());

    Assert.assertTrue(values.get(0) > 0.0);

  }
  @Test
  public void testShareManagerRate()
  {
    TreeMap<String, Double> fixed = new TreeMap<>();
    fixed.put("pool", 0.02);
    ShareManager sm = new ShareManager(fixed);
    sm.record("a", 49);
    sm.record("b", 49);

    Map<String, Double> map = sm.getPayRatios();

    Assert.assertEquals(3, map.size());
    
    assertNear(49.0, map.get("a"), 0.00001);
    assertNear(49.0, map.get("b"), 0.00001);
    assertNear(2.0, map.get("pool"), 0.00001);

  }


  private void assertNear(double expected, double found, double delta)
  {
    double diff = Math.abs(expected - found);
    Assert.assertTrue(String.format("%f %f %f", expected, found, delta), diff < delta);

  }


}
