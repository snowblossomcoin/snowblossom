package snowblossom.node;

import java.util.Collection;
import snowblossom.lib.AddressSpecHash;
import snowblossom.proto.Transaction;

public interface MemPoolTickleInterface
{

  public void tickleMemPool(Transaction tx, Collection<AddressSpecHash> involved_addresses);

}
