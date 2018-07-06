package snowblossom.miner;

import snowblossom.mining.proto.*;
import snowblossom.lib.*;

import com.google.protobuf.ByteString;
import java.nio.ByteBuffer;

import io.grpc.stub.StreamObserver;

public class Stubo extends SharedMiningServiceGrpc.SharedMiningServiceImplBase
{
  FieldSource src;
  public Stubo(FieldSource src)
  {
    this.src = src;
  }

  @Override
  public void getWords(GetWordsRequest req, StreamObserver<GetWordsResponce> observer)
  {
    try
    {
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
