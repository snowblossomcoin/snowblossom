package snowblossom;

import java.util.Properties;
import java.io.FileInputStream;

import java.util.StringTokenizer;
import java.util.LinkedList;
import java.util.List;

public abstract class Config
{

    public abstract void require(String key);
    public abstract String get(String key);

    public int getInt(String key)
    {
        return Integer.parseInt(get(key));
    }

    public double getDouble(String key)
    {
        return Double.parseDouble(get(key));
    }

    public List<String> getList(String key)
    {
        String big_str = get(key);

        StringTokenizer stok = new StringTokenizer(big_str, ",");

        LinkedList<String> lst = new LinkedList<String>();
        while(stok.hasMoreTokens())
        {
            String node = stok.nextToken().trim();
            lst.add(node);
        }
        return lst;
    }

    public boolean getBoolean(String key)
    {
        String v = get(key);
        if (v == null) return false;
        v = v.toLowerCase();
        if (v.equals("1")) return true;
        if (v.equals("true")) return true;
        if (v.equals("yes")) return true;
        if (v.equals("y")) return true;
        if (v.equals("on")) return true;
        if (v.equals("hell yeah")) return true;

        return false;
    }
    
    public boolean isSet(String key)
    {
        return (get(key) != null && !get(key).isEmpty());
    }
}
