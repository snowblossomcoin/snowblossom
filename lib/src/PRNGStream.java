package snowblossom.lib;

import org.apache.commons.math3.random.AbstractWell;
import org.apache.commons.math3.random.Well44497b;

import java.nio.ByteBuffer;
import java.nio.Buffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.TreeMap;

 /** not thread safe */
  public class PRNGStream
  {
    private AbstractWell src;
    private final int state_size_bytes;
    

    public PRNGStream(String seed)
    {
      Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

      // Deterministicly set inital state based on seed
      byte[] current_data;
      try
      {
        MessageDigest md;
        md = MessageDigest.getInstance("Skein-1024-1024");
        md.update(seed.getBytes());
        current_data = md.digest();
        md.reset();
      }
      catch(NoSuchAlgorithmException e)
      {
        throw new RuntimeException(e);
      }
      ByteBuffer bb=ByteBuffer.wrap(current_data);


      int first_seed_ints[] = new int[bb.capacity()/4];
      for(int i=0; i<first_seed_ints.length; i++)
      {
        first_seed_ints[i] = bb.getInt();
      }

      src = new Well44497b(first_seed_ints);


      // Beyond that many bytes, the seed method just ignores it
      // for Well 44497 bit version.
      state_size_bytes=5564;
      if (state_size_bytes % 4 != 0) throw new RuntimeException("Expectend inner RNG state size to be multiple of four");
      this.seed_ints = new int[state_size_bytes/4];
      this.seed_bytes = new byte[state_size_bytes];
    }

    public void nextBytes(byte[] b)
    {
      src.nextBytes(b);
    }

    /**
     * Mix in the given bytes into the rng state
     */


    private int seed_ints[];
    private byte seed_bytes[];
		private TreeMap<Integer, ByteBuffer> preallocated_buffs = new TreeMap<>();

    public void mixBytes(byte[] b)
    {
      if (b.length > state_size_bytes - 64) throw new RuntimeException("Probably breaking entropy");

      byte[] ex = null;
			int ex_len = state_size_bytes - b.length;
			if (!preallocated_buffs.containsKey(ex_len)) preallocated_buffs.put(ex_len, ByteBuffer.allocate(ex_len));

			ex = preallocated_buffs.get(ex_len).array();

      // Load existing data to mix in existing state
      nextBytes(ex);

      if (ex.length + b.length != state_size_bytes) throw new RuntimeException("no thing");

      // Build a buffer using existing data and mixed in data
      ByteBuffer bb=ByteBuffer.wrap(seed_bytes);

      bb.put(b);
      bb.put(ex);
      ((Buffer)bb).rewind();

      for(int i=0; i<seed_ints.length; i++)
      {
        seed_ints[i] = bb.getInt();
      }
      src.setSeed(seed_ints);

    }

  }


