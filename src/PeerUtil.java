package snowblossom;

import org.junit.Assert;
import snowblossom.proto.PeerInfo;

public class PeerUtil
{

  public static PeerInfo mergePeers(PeerInfo a, PeerInfo b)
  {
    PeerInfo.Builder n = PeerInfo.newBuilder();

    Assert.assertEquals(a.getHost(), b.getHost());
    Assert.assertEquals(a.getPort(), b.getPort());

    if (a.getLearned() > b.getLearned())
    {
      n.mergeFrom(b);
      n.mergeFrom(a);
    }
    else
    {
      n.mergeFrom(a);
      n.mergeFrom(b);
    }

    n.setLastChecked( Math.max(a.getLastChecked(), b.getLastChecked()));
    n.setLastPassed( Math.max(a.getLastPassed(), b.getLastPassed()));
    n.setLearned( Math.max(a.getLearned(), b.getLearned()));

    return n.build();
  }

  public static String getString(PeerInfo a)
  {
    return a.getHost() + ":" + a.getPort();
  }

}
