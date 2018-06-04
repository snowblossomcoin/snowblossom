package snowblossom.lib.trie;

import com.google.protobuf.ByteString;

import java.util.Comparator;


public class ByteStringComparator implements Comparator<ByteString>
{
  public int compare(ByteString a, ByteString b)
  {
    return HashUtils.getHexString(a).compareTo(HashUtils.getHexString(b)); 
  
  }

  public static int compareStatic(ByteString a, ByteString b)
  {
    return HashUtils.getHexString(a).compareTo(HashUtils.getHexString(b)); 
  
  }
}
