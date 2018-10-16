package snowblossom.lib.trie;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import org.apache.commons.codec.binary.Hex;

import java.security.MessageDigest;
import java.util.List;
import java.util.LinkedList;
import java.util.TreeMap;
import snowblossom.lib.HexUtil;

import snowblossom.trie.proto.TrieNode;
import snowblossom.trie.proto.ChildEntry;

public class HashUtils
{
  public static ByteString hashOfEmpty()
  {
    return hashConcat(ImmutableList.of());
  }

	public static ByteString hashConcat(List<ByteString> words)
	{
    try
    {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
			for(ByteString bs : words)
			{
      	md.update(bs.toByteArray());
			}
      return ByteString.copyFrom(md.digest());

    }
    catch (java.security.NoSuchAlgorithmException e)
    {
            throw new RuntimeException(e);
    }
	}

	public static String getHexString(byte[] data)
	{
    return HexUtil.getHexString(data);
	}

	public static String getHexString(ByteString bs)
	{
    return HexUtil.getHexString(bs);
	}

  public static boolean validateNodeHash(TrieNode node)
  {
    List<ByteString> hash_list = null;
    if (node.getIsLeaf())
    {
      hash_list = ImmutableList.of(node.getPrefix(), node.getLeafData());
    }
    else
    {
      hash_list = new LinkedList<>();
      hash_list.add(node.getPrefix());
      TreeMap<ByteString, ChildEntry> sortedChildren = new TreeMap<>(new ByteStringComparator());

      for(ChildEntry ce : node.getChildrenList())
      {
        sortedChildren.put(ce.getKey(), ce);
      }
      for(ChildEntry ce : sortedChildren.values())
      {
        hash_list.add(ce.getKey());
        hash_list.add(ce.getHash());
      }
    }


    ByteString hash_calc = hashConcat(hash_list);

    return node.getHash().equals(hash_calc);
  }

}
