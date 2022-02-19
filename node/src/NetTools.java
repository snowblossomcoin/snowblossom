package snowblossom.node;

import java.net.InetAddress;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.bitlet.weupnp.PortMappingEntry;
import java.util.logging.Logger;
import java.util.List;


public class NetTools
{
  private static final Logger logger = Logger.getLogger("snowblossom.node");

  public static void tryUPNP(List<Integer> ports)
  {
    try
    {
      for(int port :ports)
      {
        doUPNP(port);
      }
    }
    catch(Exception e)
    {
      logger.info("UPNP failed: " + e);
    }


  }
  public static void doUPNP(int port)
    throws Exception
  {
    logger.info("Attemping UPNP");

    GatewayDiscover discover = new GatewayDiscover();
    discover.discover();
    GatewayDevice d = discover.getValidGateway();
    if (d != null)
    {
      logger.info(String.format("Found UPNP gateway %s %s", d.getModelName(), d.getModelDescription()));
      InetAddress localAddress = d.getLocalAddress();
      PortMappingEntry portMapping = new PortMappingEntry();
      if (d.getSpecificPortMappingEntry(port,"TCP",portMapping))
      {
        String mapped = portMapping.getInternalClient();
        String local = localAddress.getHostAddress();
        if (local.equals(mapped) && (port == portMapping.getInternalPort()))
        {
          logger.info("Port already mapped to me.  Cool.");
        }
        else
        {
          logger.warning(String.format("Port %d already mapped to %s:%d. Consider using a different port.", port, mapped, portMapping.getInternalPort()));
          logger.warning(String.format("While I am on %s:%d", local, port));
        }
      }
      else
      {
        if(d.addPortMapping(port, port, localAddress.getHostAddress(),"TCP","snowblossom.node"))
        {
          logger.info("Port mapped with upnp gateway");
        }
      }
    }
    logger.info("Done with UPNP");

  }


}
