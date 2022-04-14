package snowblossom.node;

import duckutil.RateLimit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import snowblossom.proto.Transaction;

/**
 * Buffers transactions to broadcast and sends them at a fixed rate
 */
public class TxBroadcaster extends Thread
{

  private static final Logger logger = Logger.getLogger("snowblossom.peering");

  public static final int MAX_QUEUE_SIZE=2500;
  public static final double TPS=2.0;
  public static final double BURST_SEC=5.0;

  private final Peerage peerage;
  private final LinkedBlockingQueue<Transaction> queue;
  private final RateLimit rate_limit;

  public TxBroadcaster(Peerage peerage)
  {
    this.peerage = peerage;
    this.queue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
    this.rate_limit = new RateLimit(TPS, BURST_SEC);

    setName("TxBroadcaster");
    setDaemon(true);
  }

  /**
   * Might drop the TX if the buffer is full
   */
  public boolean send(Transaction tx)
  {
    return queue.offer(tx);
  }

  public void run()
  {
    while(true)
    {
      try
      {
        Transaction tx = queue.poll(2, TimeUnit.DAYS);
        if (tx != null)
        {
          rate_limit.waitForRate(1.0);
          peerage.broadcastTransaction(tx);
        }

      }
      catch(Throwable t)
      {
        logger.warning("TxBroadcast: " + t);
      }

    }


  }





}
