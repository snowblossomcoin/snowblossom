package snowblossom;

import snowblossom.proto.Transaction;
import snowblossom.proto.TransactionInner;

public class TransactionUtil
{

  /**
   * Turns any parse problems into runtime exceptions
   * so only do if you know the transaction is already valid
   */
  public static TransactionInner getInner(Transaction tx)
  {
    try
    {
      return TransactionInner.parseFrom(tx.getInnerData());
    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }
  }

}
