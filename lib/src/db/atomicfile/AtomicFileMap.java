package snowblossom.lib.db.atomicfile;

import com.google.protobuf.ByteString;
import duckutil.AtomicFileOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import snowblossom.lib.HexUtil;
import snowblossom.lib.db.DBMap;

public class AtomicFileMap extends DBMap
{
  private final File base;

  public AtomicFileMap(File base)
  {
    this.base = base;
  }

  @Override
  public void put(ByteString key, ByteString value)
  {
    base.mkdirs();

    File f = getFileForKey(key);

    try
    {
      AtomicFileOutputStream a_out = new AtomicFileOutputStream(f);
      a_out.write(value.toByteArray());
      a_out.flush();
      a_out.close();
    }
    catch(java.io.IOException e)
    {
      System.out.println("Writing to: " + f);
      throw new RuntimeException(e);
    }


  }


  @Override
  public ByteString get(ByteString key)
  {
    File f = getFileForKey(key);
    if (!f.exists())
    {
      return null;
    }

    if (f.length() > 1e9)
    {
      throw new RuntimeException("File too long to read: " + f);
    }
    int sz = (int)f.length();
    byte[] buff = new byte[sz];
    try
    {
      DataInputStream din=new DataInputStream(new FileInputStream(f));
      din.readFully(buff);
      din.close();

      return ByteString.copyFrom(buff);
    }
    catch(java.io.IOException e)
    {
      throw new RuntimeException(e);
    }

  }


  public File getFileForKey(ByteString key)
  {
    String name = HexUtil.getHexString(key);
    if (name.length() ==0)
    {
      name ="null";
    }

    return new File(base, name);

  }

}
