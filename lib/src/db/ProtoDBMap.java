package snowblossom.lib.db;


import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Parser;
import snowblossom.lib.trie.ByteStringComparator;
import snowblossom.lib.HexUtil;

import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.LinkedList;
import java.util.logging.Logger;
import java.util.logging.Level;


public class ProtoDBMap<M extends Message>
{
  private static final Logger logger = Logger.getLogger("snowblossom.db");

  Parser<M> parser;
  DBMap inner;

  public ProtoDBMap(Parser<M> parser, DBMap inner)
  {
    this.parser = parser;
    this.inner = inner;
  }
  
  public void put(ByteString key, Message m)
  {
    inner.put(key, m.toByteString());
  }
  public void remove(ByteString key)
  {
    inner.remove(key);
  }


  public M get(ByteString key)
  {
    ByteString bs = inner.get(key);
    if (bs == null) return null;

    try
    {
      return parser.parseFrom(bs);
    }
    catch(InvalidProtocolBufferException e)
    {
      throw new RuntimeException(e);
    }
  }

  public Map<ByteString, M> getByPrefix(ByteString prefix, int max_reply)
  {
    Map<ByteString, ByteString> inner_result = inner.getByPrefix(prefix, max_reply);

    TreeMap<ByteString, M> m = new TreeMap<>(new ByteStringComparator());

    for(Map.Entry<ByteString, ByteString> me : inner_result.entrySet())
    {
      try
      {
        m.put(me.getKey(), parser.parseFrom(me.getValue()));
      }
      catch(InvalidProtocolBufferException e)
      {
        logger.warning(String.format("Invalid db proto in prefix: %s: %s", HexUtil.getHexString(prefix), e.toString()));
      }
    }

    return m;
  }

  public List<M> getClosest(ByteString key, int count)
  {
    List<ByteString> list_bs = inner.getClosestKeys(key, count);

    LinkedList<M> list = new LinkedList<>();
    for(ByteString bs : list_bs)
    {
      ByteString value = inner.get(bs);
      try
      {
        list.add( parser.parseFrom(value) );
      }
      catch(InvalidProtocolBufferException e)
      {
        throw new RuntimeException(e);
      }
    }
    return list;
  }

  public boolean containsKey(ByteString key)
  {
    return inner.containsKey(key);
  }

  public void putAll(Map<ByteString, M> map)
  {
    TreeMap<ByteString, ByteString> sorted = new TreeMap<>(new ByteStringComparator());
    for(Map.Entry<ByteString, M> me : map.entrySet())
    {
      sorted.put(me.getKey(), me.getValue().toByteString());
    }
    inner.putAll(sorted);
  }
} 
