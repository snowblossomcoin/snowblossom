package snowblossom.trie;

import java.util.Comparator;
import com.google.protobuf.ByteString;


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
