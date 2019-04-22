package snowblossom.client;

import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.util.proto.*;
import com.google.protobuf.ByteString;

public class AuditLog
{
  public static String init(SnowBlossomClient client, String msg)
    throws Exception
  {
    return init(client, ByteString.copyFrom(msg.getBytes()));
  }

  /**
   * Initialize a chain of audit log transaction records
   */
  public static String init(SnowBlossomClient client, ByteString msg)
    throws Exception
  {
    client.getConfig().require("audit_log_source");
    AddressSpecHash audit_log_hash = AddressUtil.getHashForAddress(
      client.getParams().getAddressPrefix(), 
      client.getConfig().get("audit_log_source"));
    
    TransactionFactoryConfig.Builder tx_config = TransactionFactoryConfig.newBuilder();
    tx_config.setSign(true);
    tx_config.setInputConfirmedThenPending(true);
    tx_config.setFeeUseEstimate(true);
    tx_config.setSendAll(true);
    tx_config.addOutputs(TransactionOutput.newBuilder().setRecipientSpecHash(audit_log_hash.getBytes()).setValue(0L).build());
    tx_config.setExtra(msg);

    TransactionFactoryResult res = TransactionFactory.createTransaction(tx_config.build(), client.getPurse().getDB(), client);

    client.getStub().submitTransaction(res.getTx());

    return new ChainHash(res.getTx().getTxHash()).toString();


  }

  public static String recordLog(SnowBlossomClient client, String msg)
    throws Exception
  {
    return recordLog(client, ByteString.copyFrom(msg.getBytes()));
  }


  /**
   * Send the next audit log record in a chaing of records
   */
  public static String recordLog(SnowBlossomClient client, ByteString msg)
    throws Exception
  {
    client.getConfig().require("audit_log_source");
    AddressSpecHash audit_log_hash = AddressUtil.getHashForAddress(
      client.getParams().getAddressPrefix(), 
      client.getConfig().get("audit_log_source"));
    
    TransactionFactoryConfig.Builder tx_config = TransactionFactoryConfig.newBuilder();
    tx_config.setSign(true);
    tx_config.setInputConfirmedThenPending(true);
    tx_config.setFeeUseEstimate(true);
    tx_config.setSendAll(true);
    tx_config.addOutputs(TransactionOutput.newBuilder().setRecipientSpecHash(audit_log_hash.getBytes()).setValue(0L).build());
    tx_config.setExtra(msg);

    TransactionFactoryResult res = TransactionFactory.createTransaction(tx_config.build(), client.getPurse().getDB(), client);

    Transaction tx = res.getTx();
    TransactionInner tx_inner = TransactionUtil.getInner(tx);

    boolean includes_source = false;
    for(AddressSpec claim : tx_inner.getClaimsList())
    {
      AddressSpecHash addr = AddressUtil.getHashForSpec(claim);
      if (addr.equals(audit_log_hash)) includes_source=true;
    }
    if (!includes_source)
    {
      throw new Exception("The built transaction does not include the audit_log_source.");
    }

    client.getStub().submitTransaction(res.getTx());

    return new ChainHash(res.getTx().getTxHash()).toString();

  }


}
