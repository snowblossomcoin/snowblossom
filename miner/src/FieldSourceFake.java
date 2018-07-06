package snowblossom.miner;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import org.junit.Assert;
import snowblossom.lib.*;
import snowblossom.proto.SnowPowProof;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import java.util.Set;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.junit.Assert;
import java.util.Random;


public class FieldSourceFake extends FieldSource
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");


  private final long total_words;
  private final int total_chunk;

  private final String name;

 	private final ThreadLocal<Random> threadRnd =
		 new ThreadLocal<Random>() {
				 @Override protected Random initialValue() {
						 return new Random();
		 }
 	};

  public FieldSourceFake(NetworkParams params, int field_number) throws java.io.IOException
  {
    name = "fake";

    SnowFieldInfo field_info = params.getSnowFieldInfo(field_number);

    total_chunk = (int)(field_info.getLength() / Globals.MINE_CHUNK_SIZE); 
    total_words = field_info.getLength() / SnowMerkle.HASH_LEN_LONG;

    Set<Integer> chunks = new TreeSet<Integer>();

    for(int i=0; i< total_chunk; i++)
    {
      chunks.add(i);
    }

    holding_set = ImmutableSet.copyOf(chunks);
    
  }


  @Override
  public void bulkRead(long word_index, ByteBuffer bb) throws java.io.IOException
  {
    int remain=bb.remaining();
    byte[] b = new byte[remain];
    threadRnd.get().nextBytes(b);
    bb.put(b);

    
    
  }

  @Override
  public String toString(){return "FieldSource-" + name; }

}
