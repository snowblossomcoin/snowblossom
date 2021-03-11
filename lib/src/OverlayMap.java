package snowblossom.lib;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.Collection;

/**
 * Acts as a wrapper around an underlying map.
 * Doesn't modify underlying map, and puts are only in this
 * overlay.
 * Supports puts, gets and containsKey operations.
 */
public class OverlayMap<K,V> implements Map<K,V>
{
  private Map<K,V> under_map;
  private HashMap<K,V> map;

  public OverlayMap(Map<K,V> under)
  {
    this.under_map = under;
    map = new HashMap<>(256,0.5f);
  }

  @Override
  public void clear()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<K> keySet()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Collection<V> values()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<Map.Entry<K,V>> entrySet()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public int hashCode()
  {
    return under_map.hashCode() + map.hashCode(); 
  }

  @Override
  public void putAll(Map<? extends K,? extends V> m)
  {
    map.putAll(m);
  }

  @Override
  public V remove(Object key)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public V put(K key, V value)
  {
    return map.put(key, value);
  }

  @Override
  public V get(Object key)
  {
    V v = map.get(key);
    if (v != null) return v;

    return under_map.get(key);
  }


  @Override
  public boolean containsValue(Object value)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsKey(Object key)
  {
    return (map.containsKey(key) || under_map.containsKey(key));
  }

  @Override
  public boolean isEmpty()
  {
    return (map.isEmpty() && under_map.isEmpty());
  }

  @Override
  public int size()
  {
    return map.size() + under_map.size();
  }



}
