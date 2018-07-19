package snowblossom.miner;

import snowblossom.mining.proto.*;
import snowblossom.lib.*;

import com.google.protobuf.ByteString;
import java.nio.ByteBuffer;

import io.grpc.stub.StreamObserver;

public class Stubo extends SharedMiningServiceGrpc.SharedMiningServiceImplBase
{
  FieldSource src;
  int field_number;
  public Stubo(FieldSource src, int field_number)
  {
    this.src = src;
    this.field_number = field_number;
  }

  @Override
  public void getWords(GetWordsRequest req, StreamObserver<GetWordsResponce> observer)
  {
    try
    {
      if (req.getField() > 0)
      {
        if (req.getField() != field_number)
        {
          throw new java.io.IOException(
            String.format("Wrong field requested %d.  I have %d", req.getField(), field_number));
        }
      }
      GetWordsResponce.Builder builder = GetWordsResponce.newBuilder();

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
      observer.onError(e);
      observer.onCompleted();
    }

  }
}
