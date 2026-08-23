package snowblossom.shackleton;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import duckutil.PeriodicThread;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.lib.Globals;
import snowblossom.proto.Block;
import snowblossom.proto.SubscribeBlockTemplateRequest;
import snowblossom.proto.Transaction;
import snowblossom.shackleton.ChainIndex.NetStats;

/**
 * Keeps a fresh view of the mempool's transactions.
 *
 * The node has no RPC that lists the whole mempool (the mempool calls are all
 * per address), but it happily hands out block templates, and a template is
 * exactly the mempool's transactions selected for the next block, best fee
 * first.  So this asks for a template whenever the node reports pending
 * transactions and takes the transaction list out of it, coinbase excluded.
 *
 * Consequences worth knowing:
 *  - If the mempool ever holds more than one block's worth, the view is the
 *    next block's selection, not everything.  The page says so.
 *  - A template is for one shard.  On a network with several building shards,
 *    transactions queued for the other shards are counted by mem_pool_size but
 *    not listed here.
 *  - The node refuses templates while it is not synced.
 */
public class MempoolIndex extends PeriodicThread
{
  private static final Logger logger = Logger.getLogger("snowblossom.shackleton");

  // The template pays a reward address; for looking at the mempool any
  // address does, so use twenty zero bytes (nothing is ever mined from this).
  private static final ByteString LOOK_ADDRESS =
    ByteString.copyFrom(new byte[Globals.ADDRESS_SPEC_HASH_LEN]);

  public static class Result
  {
    public long generated;
    public int reported_pool_size;      // what the node's NodeStatus says
    public int template_shard = -1;     // shard of the template, -1 = none
    public long total_size;
    public long total_fee;
    public ImmutableList<TxView> txs = ImmutableList.of();
    public String note;                 // set when the list could not be fetched
  }

  private final Shackleton shackleton;
  private volatile Result result = new Result();

  public MempoolIndex(Shackleton shackleton)
  {
    super(10L * 1000L);
    this.shackleton = shackleton;
    setName("MempoolIndex");
    setDaemon(true);
  }

  public Result getResult() { return result; }

  @Override
  public void runPass() throws Exception
  {
    Result r = new Result();
    r.generated = System.currentTimeMillis();

    NetStats ns = shackleton.getIndex().getNetStats();
    if (ns == null)
    {
      r.note = "the explorer's chain index is still starting up";
      result = r;
      return;
    }
    r.reported_pool_size = ns.mem_pool_size;
    if (ns.mem_pool_size == 0)
    {
      // Nothing pending; no need to make the node build a template
      result = r;
      return;
    }

    try
    {
      Block template = fetchTemplate();
      if ((template == null) || (template.getHeader().getTarget().size() == 0))
      {
        r.note = "the node did not provide a block template (it may still be syncing)";
      }
      else
      {
        r.template_shard = template.getHeader().getShardId();
        ArrayList<TxView> lst = new ArrayList<>();
        for (Transaction tx : template.getTransactionsList())
        {
          // Make the transactions immediately viewable on /tx/ pages and
          // resolvable as parents of others
          shackleton.getCache().cacheTransaction(tx);
        }
        for (Transaction tx : template.getTransactionsList())
        {
          TxView tv = TxView.of(tx, shackleton.getCache(), true);
          if (tv.coinbase) continue;
          lst.add(tv);
          r.total_size += tv.size;
          r.total_fee += tv.fee;
        }
        r.txs = ImmutableList.copyOf(lst);
      }
    }
    catch (Throwable t)
    {
      logger.log(Level.FINE, "Mempool template fetch failed: " + t);
      r.note = "the node did not answer the template request";
    }
    result = r;
  }

  /** One template via the streaming RPC: take the first answer, let the deadline close the stream. */
  private Block fetchTemplate() throws Exception
  {
    final CountDownLatch latch = new CountDownLatch(1);
    final Block[] holder = new Block[1];

    SubscribeBlockTemplateRequest req = SubscribeBlockTemplateRequest.newBuilder()
      .setPayRewardToSpecHash(LOOK_ADDRESS)
      .build();

    shackleton.getAsyncStub().withDeadlineAfter(8, TimeUnit.SECONDS).subscribeBlockTemplate(req,
      new io.grpc.stub.StreamObserver<Block>()
      {
        public void onNext(Block b) { if (holder[0] == null) holder[0] = b; latch.countDown(); }
        public void onError(Throwable t) { latch.countDown(); }
        public void onCompleted() { latch.countDown(); }
      });

    latch.await(9, TimeUnit.SECONDS);
    return holder[0];
  }
}
