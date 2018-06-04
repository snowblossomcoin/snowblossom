package snowblossom.lib.db.lobstack;

import com.google.protobuf.ByteString;
import snowblossom.lib.db.DBMap;
import lobstack.Lobstack;
import snowblossom.lib.HexUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;

public class LobstackDBMap extends DBMap
{
  private Lobstack stack;
  private String prefix;

  public LobstackDBMap(Lobstack stack, String name)
  {
    this.stack = stack;
    this.prefix = name + "/";
  }

  @Override
  public ByteString get(String key)
  {
    try
    {
      ByteBuffer bb = stack.get(prefix + key);
      if (bb == null) return null;
      return ByteString.copyFrom(bb.array());
    }
    catch(java.io.IOException e)
    {
      throw new RuntimeException(e);
    }
  }

  @Override 
  public void put(String key, ByteString value)
  {
    try
    {
      stack.put(prefix + key, ByteBuffer.wrap(value.toByteArray()));
    }
    catch(java.io.IOException e)
    {
      throw new RuntimeException(e);
    }
  }

  @Override 
  public ByteString get(ByteString key)
  {
    return this.get(HexUtil.getHexString(key));
  }
  @Override 
  public void put(ByteString key, ByteString value)
  {
     this.put(HexUtil.getHexString(key), value);
  }


  @Override 
  public void putAll(SortedMap<ByteString, ByteString> m)
  {
    HashMap<String, ByteBuffer> m2 = new HashMap<>(m.size() *2, 0.75f);

    for(Map.Entry<ByteString, ByteString> me : m.entrySet())
    {
      m2.put( prefix + HexUtil.getHexString(me.getKey()), ByteBuffer.wrap(me.getValue().toByteArray()));
    }
    try
    {
      stack.putAll(m2);
    }
    catch(java.io.IOException e)
    {
      throw new RuntimeException(e);
    }

  }


}
