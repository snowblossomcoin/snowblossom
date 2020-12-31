package snowblossom.lib.db.atomicfile;

import com.google.protobuf.ByteString;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import snowblossom.lib.HexUtil;
import snowblossom.lib.db.DBMapMutationSet;
import java.util.LinkedList;
import java.util.List;

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

      FileOutputStream f_out = new FileOutputStream(f);
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

    if (dir.isDirectory())
    {
      for(String s : dir.list())
      {
        lst.add( HexUtil.hexStringToBytes(s) ); 
        if (lst.size() >= max_reply) break;
      }

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
    String value_name = HexUtil.getHexString(value);

    return new File(getFileForKey(key), value_name);

  }

}
