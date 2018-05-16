package snowblossom;


import org.apache.commons.codec.binary.Hex;
import com.google.protobuf.ByteString;

import java.math.BigInteger;

  public class SnowFieldInfo
  {
    private final String name;
    private final long length;
    private final ByteString merkle_root_hash;
    private final BigInteger activation_target;

    public SnowFieldInfo(String name, long length, String hash)
    {
      this(name, length, hash, 60);
    }
    public SnowFieldInfo(String name, long length, String hash, int activation_bits)
    {
      this.name = name;
      this.length = length;
      activation_target = BlockchainUtil.getTargetForDiff(activation_bits);

      try
      {
        merkle_root_hash = ByteString.copyFrom(Hex.decodeHex(hash));
      }
      catch(org.apache.commons.codec.DecoderException e)
      {
        throw new RuntimeException(e);
      }
    }

    public String getName(){return name;}
    public long getLength(){return length;}
    public ByteString getMerkleRootHash(){return merkle_root_hash;}
    public BigInteger getActivationTarget() { return activation_target; }

  }

