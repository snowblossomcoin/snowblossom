package snowblossom.node;

import java.util.logging.Logger;

public class StatusLogger implements StatusInterface
{
  private static final Logger logger = Logger.getLogger("snowblossom.node");

  public void setStatus(String status)
  {
    logger.info(status);
  }

}
