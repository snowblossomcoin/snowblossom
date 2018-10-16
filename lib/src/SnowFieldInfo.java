package snowblossom.lib;


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

      merkle_root_hash = HexUtil.hexStringToBytes(hash);
    }

    public String getName(){return name;}
    public long getLength(){return length;}
    public ByteString getMerkleRootHash(){return merkle_root_hash;}
    public BigInteger getActivationTarget() { return activation_target; }

  }

