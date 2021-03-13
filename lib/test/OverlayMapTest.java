package lib.test;

import java.util.TreeMap;
import java.util.Map;
import snowblossom.lib.OverlayMap;
import com.google.common.collect.ImmutableMap;


import org.junit.Assert;
import org.junit.Test;

public class OverlayMapTest
{
  @Test
  public void testBasicNoCache()
  {
    Map<String, Long> base = ImmutableMap.of("a",0L,"b",1L);

    OverlayMap<String,Long> o = new OverlayMap<>(base, false);

    Assert.assertEquals(base.get("a"), o.get("a"));
    Assert.assertEquals(base.get("b"), o.get("b"));
    Assert.assertEquals(base.get("c"), o.get("c"));

    o.put("d",4L);
    Assert.assertEquals(4L, (long)o.get("d"));
    Assert.assertTrue( o.containsKey("d"));

    Assert.assertEquals(base.containsKey("a"), o.containsKey("a"));
    Assert.assertEquals(base.containsKey("b"), o.containsKey("b"));
    Assert.assertEquals(base.containsKey("c"), o.containsKey("c"));


  }

  @Test
  public void testBasicCache()
  {
    Map<String, Long> base = ImmutableMap.of("a",0L,"b",1L);

    OverlayMap<String,Long> o = new OverlayMap<>(base, true);

    Assert.assertEquals(base.get("a"), o.get("a"));
    Assert.assertEquals(base.get("b"), o.get("b"));
    Assert.assertEquals(base.get("c"), o.get("c"));

    o.put("d",4L);
    Assert.assertEquals(4L, (long)o.get("d"));
    Assert.assertTrue( o.containsKey("d"));

    Assert.assertEquals(base.containsKey("a"), o.containsKey("a"));
    Assert.assertEquals(base.containsKey("b"), o.containsKey("b"));
    Assert.assertEquals(base.containsKey("c"), o.containsKey("c"));
  }

  @Test
  public void testBasicModCache()
  {
    Map<String, Long> base = ImmutableMap.of("a",0L,"b",1L);
    TreeMap<String, Long> base_m = new TreeMap<>();
    base_m.putAll(base);

    OverlayMap<String,Long> o = new OverlayMap<>(base_m, true);

    o.put("d",4L);
    Assert.assertEquals(base.get("a"), o.get("a"));
    Assert.assertEquals(base.get("b"), o.get("b"));
    Assert.assertEquals(base.get("c"), o.get("c"));

    Assert.assertEquals(4L, (long)o.get("d"));
    Assert.assertTrue( o.containsKey("d"));

    Assert.assertEquals(base.containsKey("a"), o.containsKey("a"));
    Assert.assertEquals(base.containsKey("b"), o.containsKey("b"));
    Assert.assertEquals(base.containsKey("c"), o.containsKey("c"));

    base_m.clear();
    base_m.put("c",3L);
    base_m.put("z",26L);

    // This should all be cached values, so shouldn't matter that base_m was clobbered
    Assert.assertEquals(base.get("a"), o.get("a"));
    Assert.assertEquals(base.get("b"), o.get("b"));
    Assert.assertEquals(base.get("c"), o.get("c"));

    Assert.assertEquals(4L, (long)o.get("d"));
    Assert.assertTrue( o.containsKey("d"));

    Assert.assertEquals(base.containsKey("a"), o.containsKey("a"));
    Assert.assertEquals(base.containsKey("b"), o.containsKey("b"));
    Assert.assertEquals(base.containsKey("c"), o.containsKey("c"));

    // But we still read this cached value right
    Assert.assertEquals(base_m.get("z"), o.get("z"));
    Assert.assertEquals(base_m.containsKey("z"), o.containsKey("z"));

  }



}
