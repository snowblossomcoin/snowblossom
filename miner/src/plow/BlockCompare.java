package snowblossom.miner.plow;

import com.google.protobuf.ByteString;
import duckutil.Config;
import duckutil.ConfigFile;
import duckutil.PeriodicThread;
import duckutil.TimeRecord;
import duckutil.jsonrpc.JsonRpcServer;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import snowblossom.client.StubUtil;
import snowblossom.lib.*;
import snowblossom.lib.db.DB;
import snowblossom.lib.db.lobstack.LobstackDB;
import snowblossom.lib.db.rocksdb.JRocksDB;
import snowblossom.lib.db.atomicfile.AtomicFileDB;
import snowblossom.mining.proto.*;
import snowblossom.proto.*;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import com.google.common.collect.ImmutableList;


public class BlockCompare implements Comparable<BlockCompare>
{
  private final BlockTemplate bt;

  public BlockCompare(BlockTemplate bt)
  {
    this.bt = bt;
  }

  public BlockTemplate getBlockTemplate(){return bt;}

  @Override
  public boolean equals(Object o)
  {
    throw new RuntimeException("Leave me alone");
  }

  @Override
  public int hashCode()
  {
    return bt.hashCode();
  }

  public double getRewardPerHash()
  {
    double diff = PowUtil.getDiffForTarget(
      BlockchainUtil.targetBytesToBigInteger(
        bt.getBlock().getHeader().getTarget()));
    double hashes = Math.pow(2.0, diff);
  
    Transaction coinbase = bt.getBlock().getTransactions(0);
    TransactionInner inner = TransactionUtil.getInner(coinbase);
    double reward = 0.0;
    for(TransactionOutput out : inner.getOutputsList())
    {
      reward += out.getValue();
    }

    return reward/hashes;

  }

  // First item is best item
  @Override
  public int compareTo(BlockCompare o)
  {
    if (bt.getAdvancesShard() > o.bt.getAdvancesShard()) return -1;
    if (bt.getAdvancesShard() < o.bt.getAdvancesShard()) return 1;

    if (getRewardPerHash() > o.getRewardPerHash()) return -1;
    if (getRewardPerHash() < o.getRewardPerHash()) return 1;

    if (bt.getBlock().getHeader().getBlockHeight() < o.bt.getBlock().getHeader().getBlockHeight()) return -1;
    if (bt.getBlock().getHeader().getBlockHeight() > o.bt.getBlock().getHeader().getBlockHeight()) return 1;

    if (bt.getBlock().getHeader().getTimestamp() > o.bt.getBlock().getHeader().getTimestamp()) return -1; 
    if (bt.getBlock().getHeader().getTimestamp() < o.bt.getBlock().getHeader().getTimestamp()) return 1; 

    return 0;
  }
}

