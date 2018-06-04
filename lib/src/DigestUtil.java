package snowblossom.lib;

import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import org.junit.Assert;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;


public class DigestUtil
{

  public static MessageDigest getMD()
  {
    try(TimeRecordAuto tra = TimeRecord.openAuto("MessageDigest.getMD"))
    {
      MessageDigest md = MessageDigest.getInstance(Globals.BLOCKCHAIN_HASH_ALGO);
      return md;
    }
    catch (java.security.NoSuchAlgorithmException e)
    {
      throw new RuntimeException(e);
    }
  }
  public static MessageDigest getMDAddressSpec()
  {
    try(TimeRecordAuto tra = TimeRecord.openAuto("MessageDigest.getMDAddressSpec"))
    {
      MessageDigest md = MessageDigest.getInstance(Globals.ADDRESS_SPEC_HASH_ALGO);
      return md;
    }
    catch (java.security.NoSuchAlgorithmException e)
    {
      throw new RuntimeException(e);
    }
  }

  public static ChainHash getMerkleRootForTxList(List<ChainHash> tx_list)
  {
    ArrayList<ChainHash> src = new ArrayList<>();
    src.addAll(tx_list);

    ArrayList<ChainHash> sink = new ArrayList<>();
    MessageDigest md = getMD();

    if (src.size() ==0)
    {
      throw new RuntimeException("Can't merkle empty list");
    }


    while(src.size() > 1)
    {

      for(int i=0; i<src.size(); i=i+2)
      {
        if (i+1 == src.size())
        {
          //Other implementations would hash this with itself.
          //I don't see the point of that.  Seems it would just make the merkle proof longer.

          sink.add(src.get(i)); 
        }
        else
        {
          md.update(src.get(i).toByteArray());
          md.update(src.get(i+1).toByteArray());

          sink.add(new ChainHash(md.digest()));
        }
      }
      src = sink;
      sink = new ArrayList<>();
    }
    Assert.assertEquals(1,src.size());

    return src.get(0);
  }



}
