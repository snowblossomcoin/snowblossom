package snowblossom.lib;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache<K,V> extends LinkedHashMap<K,V>
{

	private static final long serialVersionUID=9L;
	private int MAX_CAP;

	public LRUCache(int cap)
	{
		super(cap+1, 2.000f, true); 
		MAX_CAP=cap;

	}

	protected boolean removeEldestEntry(Map.Entry<K,V> eldest)
	{
		return (size() > MAX_CAP);

	}

}
