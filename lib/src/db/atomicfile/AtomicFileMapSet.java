package snowblossom.lib.db.atomicfile;

import com.google.protobuf.ByteString;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import snowblossom.lib.HexUtil;
import snowblossom.lib.DigestUtil;
import snowblossom.lib.db.DBMapMutationSet;
import java.util.LinkedList;
import java.util.List;
import java.io.FileInputStream;
import java.io.DataInputStream;
import duckutil.AtomicFileOutputStream;
import snowblossom.lib.Globals;

public class AtomicFileMapSet extends DBMapMutationSet
{
  private final File base;

  public AtomicFileMapSet(File base)
  {
    this.base = base;
  }

  @Override
  public void add(ByteString key, ByteString value)
  {
    try
    {
      File f = getFileForKeyValue(key, value);
      f.getParentFile().mkdirs();

      AtomicFileOutputStream f_out = new AtomicFileOutputStream(f);
      f_out.write(value.toByteArray());
      f_out.flush();
      f_out.close();
    }
    catch(java.io.IOException e)
    {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void remove(ByteString key, ByteString value)
  {
    File f = getFileForKeyValue(key, value);
    f.delete();
  }

  @Override
  public List<ByteString> getSet(ByteString key, int max_reply)
  {
    File dir = getFileForKey(key);

    LinkedList<ByteString> lst = new LinkedList<>();

    try
    {
      if (dir.isDirectory())
      {
        for(File f : dir.listFiles())
        {
          // Avoid picking up any tmp files
          if (f.getName().length() == Globals.BLOCKCHAIN_HASH_LEN * 2)
          {
            int sz = (int)f.length();
            byte b[]=new byte[sz];
            DataInputStream d_in = new DataInputStream(new FileInputStream(f));
            d_in.readFully(b);
            d_in.close();
            lst.add(ByteString.copyFrom(b));
          }

          if (lst.size() >= max_reply) break;
        }

      }
    }
    catch(java.io.IOException e)
    {
      throw new RuntimeException(e);
    }
    return lst;
  }



  public File getFileForKey(ByteString key)
  {
    String key_name = HexUtil.getHexString(key);
    return new File(base, key_name);

  }

  public File getFileForKeyValue(ByteString key, ByteString value)
  {
    String value_name = HexUtil.getHexString(DigestUtil.hash(value));
    return new File(getFileForKey(key), value_name);
  }

}
