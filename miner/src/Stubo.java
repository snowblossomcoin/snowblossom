package snowblossom.miner;

import snowblossom.mining.proto.*;
import snowblossom.lib.*;

import com.google.protobuf.ByteString;
import java.nio.ByteBuffer;

import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicLong;
import java.text.DecimalFormat;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Stubo extends SharedMiningServiceGrpc.SharedMiningServiceImplBase
{
  private static final Logger logger = Logger.getLogger("snowblossom.miner");

  FieldSource src;
  int field_number;
  public Stubo(FieldSource src, int field_number)
  {
    this.src = src;
    this.field_number = field_number;
  }

  protected AtomicLong call_counter = new AtomicLong();
  protected AtomicLong read_counter = new AtomicLong();

  @Override
  public void getWords(GetWordsRequest req, StreamObserver<GetWordsResponce> observer)
  {
    try
    {
      GetWordsResponce.Builder builder = GetWordsResponce.newBuilder();
      if (req.getField() > 0)
      {
        if (req.getField() != field_number)
        {
          builder.setWrongField(true);
          observer.onNext(builder.build());
          observer.onCompleted();
          return;
          
          
        }
      }

      call_counter.getAndIncrement();
      read_counter.getAndAdd(req.getWordIndexesCount());


      for(long x : req.getWordIndexesList())
      {
        byte[] b = new byte[Globals.SNOW_MERKLE_HASH_LEN];
        src.readWord(x, ByteBuffer.wrap(b));
        builder.addWords(ByteString.copyFrom(b));
      }
      observer.onNext(builder.build());
      observer.onCompleted();
    }
    catch(java.io.IOException e)
    {
      logger.warning("Error from client: " + e);
      observer.onError(e);
      observer.onCompleted();
    }

  }
  public String getRateString(double elapsed_sec)
  {
    double reads = read_counter.getAndSet(0L);
    double read_rate = reads / elapsed_sec;

    double calls = call_counter.getAndSet(0L);
    double call_rate = calls / elapsed_sec;
    double network = read_rate * 16.0;
    double network_mb = network / 1048576.0;

    DecimalFormat df = new DecimalFormat("0.0");
    return String.format("read_ops/s: %s rpc_ops/s: %s network_bw: %s MB/s",
      df.format(read_rate),
      df.format(call_rate),
      df.format(network_mb));

  }

}
