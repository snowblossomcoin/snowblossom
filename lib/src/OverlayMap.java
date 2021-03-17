package snowblossom.lib;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
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
  private HashSet<K> null_cache;
  private boolean include_on_read;

  /**
   * if include_on_read is set, then on containsKey or get methods
   * get cached on this level on access
   */
  public OverlayMap(Map<K,V> under, boolean include_on_read)
  {
    this.under_map = under;
    this.include_on_read = include_on_read;
    this.null_cache = new HashSet<>(256, 0.5f);
    this.map = new HashMap<>(256,0.5f);
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
    HashSet<Map.Entry<K,V>> es = new HashSet<>();
    es.addAll( under_map.entrySet() );
    es.addAll( map.entrySet() );
    return es;
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
    if (include_on_read)
    {
      null_cache.removeAll(m.keySet());
    }

  }

  @Override
  public V remove(Object key)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public V put(K key, V value)
  {
    if (include_on_read)
    {
      null_cache.remove(key);
    }
    return map.put(key, value);
  }

  @Override
  public V get(Object key)
  {
    if (null_cache.contains(key)) return null;

    V v = map.get(key);
    if (v != null) return v;

    v = under_map.get(key);
    if(include_on_read)
    {
      if (v!=null) map.put((K)key, v);
      else null_cache.add((K)key);
    }

    return v;
  }


  @Override
  public boolean containsValue(Object value)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean containsKey(Object key)
  {
    if (map.containsKey(key)) return true;
    if (null_cache.contains(key)) return false;

    boolean has = under_map.containsKey(key);

    if (include_on_read)
    {
      if (has)
      {
        map.put((K)key, under_map.get(key)); 
      }
      else
      {
        null_cache.add((K)key);
      }
    }
    return has;
  }

  @Override
  public boolean isEmpty()
  {
    return (map.isEmpty() && under_map.isEmpty());
  }

  // This will over estiamte
  @Override
  public int size()
  {
    return entrySet().size();
  }



}
