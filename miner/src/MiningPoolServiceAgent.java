package snowblossom.miner;

import snowblossom.proto.*;
import snowblossom.mining.proto.*;
import snowblossom.lib.*;
import io.grpc.stub.StreamObserver;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.HashSet;
import java.nio.ByteBuffer;

import com.google.protobuf.ByteString;

import java.util.concurrent.atomic.AtomicInteger;


public class MiningPoolServiceAgent extends MiningPoolServiceGrpc.MiningPoolServiceImplBase
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  private MrPlow plow;

  private AtomicInteger next_work_id = new AtomicInteger(new Random().nextInt());
  private LinkedList<MinerInfo> miner_list=new LinkedList<>();
  private Block last_block;
  private HashMap<Integer, WorkInfo> pending_work = new HashMap<>();

  public MiningPoolServiceAgent(MrPlow plow)
  {
    this.plow = plow;
  }

  @Override
  public void getWork(GetWorkRequest req, StreamObserver<WorkUnit> observer)
  {
    MinerInfo info = new MinerInfo(req, observer);

    synchronized(miner_list)
    {
      miner_list.add(info);
    }
    if (last_block!=null)
    {
      sendWork(info, last_block);
    }

  }

  public int getMinerConnectionCount()
  {
    synchronized(miner_list)
    {
      return miner_list.size();
    }
  }

  @Override
  public void submitWork(WorkSubmitRequest req, StreamObserver<SubmitReply> observer)
  {
    try
    {
      int work_id = req.getWorkId();
      BlockHeader submit_header = req.getHeader();
      WorkInfo wi = null;
      synchronized(pending_work)
      {
        wi = pending_work.get(work_id);
      }
      if (wi == null) throw new ValidationException("Unknown work id: " + work_id);

      if (submit_header.getSnowField() < wi.blk.getHeader().getSnowField())
      {
        throw new ValidationException("Too low of snow field");
      }

      if (!PowUtil.lessThanTarget(submit_header.getSnowHash().toByteArray(), wi.wu.getReportTarget()))
      {
        throw new ValidationException("Hash larger than reporting target");
      }
      byte[] nonce = new byte[4];
      ByteBuffer bb = ByteBuffer.wrap(nonce);
      bb.putInt(work_id);

      if (!submit_header.getNonce().startsWith(ByteString.copyFrom(nonce)))
      {
        throw new ValidationException("Nonce has wrong prefix");
      }

      synchronized(wi)
      {
        if (wi.used_nonces.contains(submit_header.getNonce()))
        {
          throw new ValidationException("Nonce already used on this work unit");
        }
        wi.used_nonces.add(submit_header.getNonce());
      }

      BlockHeader formed_header = BlockHeader.newBuilder()
        .mergeFrom(wi.blk.getHeader())
        .setSnowField(submit_header.getSnowField())
        .setNonce(submit_header.getNonce())
        .setSnowHash(submit_header.getSnowHash())
        .addAllPowProof(submit_header.getPowProofList())
        .build();

      Validation.checkBlockHeaderBasics( plow.getParams(), formed_header, true );

      long shares = 1;
      int diff_delta = wi.work_diff - plow.getMinDiff();
      shares = 1L << diff_delta;
      long hashes = 1L << wi.work_diff;

      recordShare(wi.miner_info, shares, hashes);

      wi.miner_info.reportShare();

      if (PowUtil.lessThanTarget(formed_header.getSnowHash().toByteArray(), formed_header.getTarget()))
      {
        //submit real block
        Block real_block = Block.newBuilder()
          .mergeFrom(wi.blk)
          .setHeader(formed_header)
          .build();

        SubmitReply reply = plow.getBlockingStub().submitBlock(real_block);
        logger.info("REAL BLOCK SUBMIT: " + reply);
        if (plow.getDB().getSpecialMapSet() != null)
        {
          plow.getDB().getSpecialMapSet().add(MrPlow.BLOCK_KEY, formed_header.toByteString());
        }
      }

      observer.onNext( SubmitReply.newBuilder().setSuccess(true).build() );
      observer.onCompleted();
    }
    catch(Throwable t)
    {
      observer.onNext( SubmitReply.newBuilder().setSuccess(false).setErrorMessage(t.toString()).build() );
      observer.onCompleted();
    }

  }

  private void sendWork(MinerInfo info, Block blk)
  {
    int work_id = next_work_id.incrementAndGet();
    byte[] nonce=new byte[4];
    ByteBuffer bb = ByteBuffer.wrap(nonce);
    bb.putInt(work_id);

    BlockHeader header = BlockHeader.newBuilder()
      .mergeFrom(blk.getHeader())
      .setNonce(ByteString.copyFrom(nonce))
      .build();

    ByteString target = BlockchainUtil.targetBigIntegerToBytes(BlockchainUtil.getTargetForDiff(info.working_diff));

    WorkUnit wu = WorkUnit.newBuilder()
      .setHeader(header)
      .setWorkId(work_id)
      .setReportTarget(target)
      .build();

    WorkInfo wi = new WorkInfo(wu, info, blk, info.working_diff);

    synchronized(pending_work)
    {
      pending_work.put(work_id, wi);
    }
    info.observer.onNext(wu);

  }

  public void updateBlockTemplate(Block blk)
  {
    Block old = last_block;
    if (last_block != null)
    if (last_block.getHeader().getBlockHeight() < blk.getHeader().getBlockHeight())
    {
      //Clear all pending work
      synchronized(pending_work)
      {
        pending_work.clear();
      }

    }
    
    last_block = blk;

    synchronized(miner_list)
    {
      LinkedList<MinerInfo> keep_list = new LinkedList<MinerInfo>();
      int send_count = miner_list.size();
      int keep_count = 0;
      int drop_count = 0;

      for(MinerInfo mi : miner_list)
      {
        try
        {
          sendWork(mi, blk);
          keep_list.add(mi);
          keep_count++;
        }
        catch(Throwable t)
        {
          drop_count++;
          //logger.info("Error in send work: " + t);
        } 

      }

      miner_list.clear();
      miner_list.addAll(keep_list);

      logger.info(String.format("Send new work to %d workers.  Keeping %d, Dropping %d", send_count, keep_count, drop_count));

    }

  }

  public class MinerInfo
  {
    public final GetWorkRequest req;
    public final StreamObserver<WorkUnit> observer;
    public int working_diff = plow.getMinDiff();
    public long last_shift = 0;

    public LinkedList<Long> share_times = new LinkedList<>();


    public MinerInfo(GetWorkRequest req, StreamObserver<WorkUnit> observer)
    {
      this.req = req;
      this.observer = observer;
    }

    public void reportShare()
    {
      synchronized(share_times)
      {
        share_times.add(System.currentTimeMillis());
        long expire = System.currentTimeMillis() - MrPlow.SHARE_VIEW_WINDOW;
        while((share_times.size() > 0) && (share_times.peek() < expire))
        {
          share_times.poll();
        }
        if (share_times.size() >= MrPlow.SHARES_IN_VIEW_FOR_UPTARGET)
        {
          working_diff++;
          share_times.clear();
          last_shift = System.currentTimeMillis();
          // We don't care when the last shift was, if this gets hit, it gets hit
        }
        else if (share_times.size() < MrPlow.SHARES_IN_VIEW_FOR_DOWNTARGET)
        {
          if (last_shift + MrPlow.SHARE_VIEW_WINDOW < System.currentTimeMillis())
          {
            // Intentionally not clearing the share_times here to avoid 
            // this being run again on the next share coming in
            working_diff = Math.max(working_diff - 1, plow.getMinDiff());
            last_shift = System.currentTimeMillis();
          }
        }

      }

    }

  }

  public class WorkInfo
  {
    public final WorkUnit wu;
    public final MinerInfo miner_info;
    public final Block blk;
    public final int work_diff;

    public HashSet<ByteString> used_nonces=new HashSet<>();

    public WorkInfo(WorkUnit wu, MinerInfo miner_info, Block blk, int work_diff)
    {
      this.wu = wu;
      this.miner_info = miner_info;
      this.blk = blk;
      this.work_diff = work_diff;
    }

  }


  public void recordShare(MinerInfo info, long shares, long hashes)
  {
    logger.info(String.format("Share recorded for %s - (%d shares) (%d hashes)", info.req.getPayToAddress(), shares, hashes));
    plow.getShareManager().record(info.req.getPayToAddress(), shares);
    plow.recordHashes(hashes);
    plow.getReportManager().record(info.req.getPayToAddress(), hashes);
  }
}


