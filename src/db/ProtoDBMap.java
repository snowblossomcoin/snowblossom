package snowblossom.db;


import com.google.protobuf.Parser;
import com.google.protobuf.Message;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Map;
import java.util.TreeMap;

public class ProtoDBMap<M extends Message>
{
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

  public boolean containsKey(ByteString key)
  {
    return inner.containsKey(key);
  }

}
