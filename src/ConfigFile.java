package snowblossom;

import java.util.Properties;
import java.io.FileInputStream;

import java.util.StringTokenizer;
import java.util.LinkedList;
import java.util.List;

public class ConfigFile extends Config
{
    private Properties props;

    public ConfigFile(String file_name)
        throws java.io.IOException
    {
        props = new Properties();

        props.load(new FileInputStream(file_name));
    }

    @Override
    public String get(String key)
    {
        return props.getProperty(key);
    }
}
