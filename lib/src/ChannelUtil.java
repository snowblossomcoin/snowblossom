package snowblossom.lib;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;


public class ChannelUtil
{
  public static void readFully(FileChannel channel, ByteBuffer bb, long pos)
    throws java.io.IOException
  {
    while(bb.remaining() > 0)
    {
      channel.read(bb,pos);
    }
  }
}
