package snowblossom.node;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import duckutil.LRUCache;
import duckutil.Pair;
import duckutil.PeriodicThread;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.ArrayList;                                                                                             import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;                                                                                               import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;                                                                                                import java.util.Scanner;                                                                                               import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;
import org.junit.Assert;                                                                                                import snowblossom.lib.*;
import snowblossom.lib.trie.HashUtils;
import snowblossom.proto.*;


public class GoldSetFinder
{
	private ByteString GOLD_MAP_KEY = ByteString.copyFrom("forge".getBytes());
  private SnowBlossomNode node;
	private static final Logger logger = Logger.getLogger("snowblossom.node");

  public GoldSetFinder(SnowBlossomNode node)
  {
    this.node = node;
  }

	private Map<Integer, BlockHeader> loadGoldSet()
	{
		GoldSet gs = node.getDB().getGoldSetMap().get(GOLD_MAP_KEY);
		if (gs == null) return null;

		Map<Integer, BlockHeader> gold = new TreeMap<>();

		for(Map.Entry<Integer,ByteString> me : gs.getShardToHashMap().entrySet())
		{
			int shard = me.getKey();
			ChainHash hash = new ChainHash(me.getValue());

			BlockHeader h = node.getForgeInfo().getHeader(hash);
			if (h == null)
			{
				logger.warning(String.format("Unable to load gold set, header not found: %d %s", shard, hash.toString()));
				return null;
			}
			gold.put(shard, h);
		}
		return gold;

	}


}
