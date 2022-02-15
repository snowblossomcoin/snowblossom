package snowblossom.node;

import duckutil.PeriodicThread;
import com.google.protobuf.ByteString;
import snowblossom.proto.BlockHeader;
import java.util.Random;

public class DBMaintThread extends PeriodicThread
{
  private SnowBlossomNode node;

  private final int maint_gap;
  public DBMaintThread(SnowBlossomNode node)
  {
    super(60000);
    this.node = node;

    int base = 15*144; // 15 days

    Random rnd = new Random();

    // Pick a time between 15 days and 30 days
    // We want to be random in case there is some way to exploit
    // that a node is going to be a little slow
    // and we certainly don't want a bunch of nodes doing it at once
    // even if they all update at the same time (and restart at the same time)
    maint_gap = base + rnd.nextInt(base);

  }


  public void runPass() 
    throws Exception
  {
    int maint_height = 0;
    ByteString db_maint_data = node.getDB().getSpecialMap().get("db_maint_height");
    if(db_maint_data != null)
    {
      maint_height = Integer.parseInt( new String( db_maint_data.toByteArray()));
    }
    int curr_height = 0;
    
    BlockHeader high = node.getPeerage().getHighestSeenHeader();
    if (high != null)
    {
      curr_height = high.getBlockHeight();
    }
    if (curr_height >= maint_height + maint_gap)
    {
      node.getDB().interactiveMaint();
      String curr_str = "" + curr_height;


      node.getDB().getSpecialMap().put("db_maint_height", ByteString.copyFrom(curr_str.getBytes()));
    }
    

  }




}
