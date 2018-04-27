package snowblossom;


import org.apache.commons.codec.binary.Hex;
import com.google.protobuf.ByteString;


  public class SnowFieldInfo
  {
    private final String name;
    private final long length;
    private final ByteString merkle_root_hash;

    public SnowFieldInfo(String name, long length, String hash)
    {
      this.name = name;
      this.length = length;

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

  }

