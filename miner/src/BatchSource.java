package snowblossom.miner;

import com.google.protobuf.ByteString;
import java.util.List;

public interface BatchSource
{
  public int getSuggestedBatchSize();
  public List<ByteString> readWordsBulk(List<Long> indexes);

}
