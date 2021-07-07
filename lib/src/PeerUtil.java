package snowblossom.lib;

import org.junit.Assert;
import snowblossom.proto.PeerInfo;
import java.net.URI;

public class PeerUtil
{

  public static PeerInfo mergePeers(PeerInfo a, PeerInfo b)
  {
    PeerInfo.Builder n = PeerInfo.newBuilder();

    Assert.assertEquals(a.getHost(), b.getHost());
    Assert.assertEquals(a.getPort(), b.getPort());

    if (a.getLearned() > b.getLearned())
    {
      n.mergeFrom(a);
    }
    else
    {
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

  public static boolean isSane(PeerInfo a)
  {
    if (a.toByteString().size() > 8192) return false;
    if (a.getHost().length() < 1) return false;
    if (a.getHost().length() > 255) return false;
    if (a.getPort() <= 0) return false;
    if (a.getPort() > 65535) return false;
    if (!HexUtil.getSafeString(a.getHost()).equals(a.getHost())) return false;
    if (!HexUtil.getSafeString(a.getVersion()).equals(a.getVersion())) return false;
    if (a.getNodeId().size() > Globals.MAX_NODE_ID_SIZE) return false;
    if (a.getLastChecked() > System.currentTimeMillis()) return false;
    if (a.getLastPassed() > System.currentTimeMillis()) return false;
    if (a.getLearned() > System.currentTimeMillis()) return false;

    return true;

  }

  public static PeerInfo getPeerInfoFromUri(String uri, NetworkParams params)
  {
    try
    {
      URI u = new URI(uri);

      String host = u.getHost();
      int port = u.getPort();
      String scheme = u.getScheme();
      if (scheme == null) scheme="grpc";

      PeerInfo.Builder pi = PeerInfo.newBuilder();
      pi.setHost(host);

      if (scheme.equals("grpc"))
      {
        if (port == -1)
        {	
          port = params.getDefaultPort();
        }
        pi.setConnectionType(PeerInfo.ConnectionType.GRPC_TCP);
      }
      else if (scheme.equals("grpc+tls"))
      {
        if (port == -1)
        {
          port = params.getDefaultTlsPort();
        }
        pi.setConnectionType(PeerInfo.ConnectionType.GRPC_TLS);
      }
      else
      {
        throw new Exception("Unknown scheme: " + scheme);
      }

      pi.setPort(port);

      return pi.build();
    }
    catch(Exception e)
    {
      return null;
    }
  }


}
