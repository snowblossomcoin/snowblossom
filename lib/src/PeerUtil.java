package snowblossom.lib;

import java.net.URI;
import java.util.TreeSet;
import org.junit.Assert;
import snowblossom.lib.tls.MsgSigUtil;
import snowblossom.lib.trie.ByteStringComparator;
import snowblossom.proto.AddressSpec;
import snowblossom.proto.PeerInfo;
import snowblossom.proto.SignedMessagePayload;

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

  public static boolean isSane(PeerInfo a, NetworkParams params)
  {
    if (a.toByteString().size() > 16000) return false;
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
    if (a.getNodeSnowAddress().size() > Globals.ADDRESS_SPEC_HASH_LEN) return false;
    if (a.getTrustnetAddress().size() > Globals.ADDRESS_SPEC_HASH_LEN) return false;

    for(int shard_id : a.getShardIdSetList())
    {
      if (shard_id < 0) return false;
      if (shard_id > params.getMaxShardId()) return false;
    }

    // If there is a claim of a trustnet, it must be signed
    if (a.getTrustnetAddress().size() > 0)
    {
      try
      {
        SignedMessagePayload payload = MsgSigUtil.validateSignedMessage(a.getTrustnetSignedPeerInfo(), params);
        PeerInfo b = payload.getPeerInfo();
        AddressSpec claim = payload.getClaim();
        AddressSpecHash signed_by = AddressUtil.getHashForSpec(claim);
        if (!signed_by.equals(a.getTrustnetAddress())) return false;

        // At this point, the peer info has a signed version and it is signed by the claimed trustnet address
        if (!a.getHost().equals(b.getHost())) return false;
        if (a.getPort() != b.getPort()) return false;
        if (ByteStringComparator.compareStatic(a.getNodeId(), b.getNodeId()) != 0) return false;
        if (ByteStringComparator.compareStatic(a.getNodeSnowAddress(), b.getNodeSnowAddress()) != 0) return false;
        if (ByteStringComparator.compareStatic(a.getTrustnetAddress(), b.getTrustnetAddress()) != 0) return false;
        TreeSet<Integer> a_set = new TreeSet<Integer>(); a_set.addAll(a.getShardIdSetList());
        TreeSet<Integer> b_set = new TreeSet<Integer>(); b_set.addAll(b.getShardIdSetList());
        if (!a_set.equals(b_set)) return false;

      }
      catch(ValidationException e)
      {
        return false;
      }

    }
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
      e.printStackTrace();
      return null;
    }
  }


}
