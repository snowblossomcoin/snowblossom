package snowblossom.client;

import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.util.proto.*;

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

  public static AuditLogReport getAuditReport(SnowBlossomClient client, AddressSpecHash audit_log_hash)
  {
    RequestAddress req_addr = RequestAddress.newBuilder().setAddressSpecHash( audit_log_hash.getBytes() ).build();
    HistoryList history_list = client.getStub().getAddressHistory( req_addr );
    TransactionHashList mempool_list = client.getStub().getMempoolTransactionList(req_addr);

    if (history_list.getNotEnabled()) throw new RuntimeException("Node does not support history.  Unable to log.");

    HashMap<ChainHash, Integer> tx_height_map = new HashMap<>();
    LinkedList<ByteString> addr_list = new LinkedList<>();
    for( HistoryEntry he : history_list.getEntriesList())
    {
      
      tx_height_map.put(new ChainHash(he.getTxHash()), he.getBlockHeight());
      addr_list.add(he.getTxHash());
    }
    for (ByteString tx_hash : mempool_list.getTxHashesList())
    {
      addr_list.add(tx_hash);
    }

    HashMap<ChainHash, Transaction> tx_map = new HashMap<>();
    HashSet<ChainHash> spent_tx = new HashSet<>();
    for(ByteString tx_hash : addr_list)
    {
      Transaction tx = client.getStub().getTransaction( RequestTransaction.newBuilder().setTxHash(tx_hash).build() );
      tx_map.put(new ChainHash(tx_hash), tx);

      if (acceptableAuditTransaction(audit_log_hash, tx))
      {
        TransactionInner tx_inner = TransactionUtil.getInner(tx);
        for(TransactionInput input : tx_inner.getInputsList())
        {
          spent_tx.add( new ChainHash(input.getSrcTxId()));
        }
      }

    }

    AuditLogReport.Builder report = AuditLogReport.newBuilder();

    for(ChainHash tx_hash : tx_map.keySet())
    {
      if (!spent_tx.contains(tx_hash))
      {
        AuditLogChain chain = getAuditChain(audit_log_hash, tx_hash, tx_map, tx_height_map);
        if (chain != null)
        {
          report.addChains(chain);
        }
      }
    }

    return report.build();
  }

  public static AuditLogChain getAuditChain(AddressSpecHash audit_log_hash, ChainHash head, 
    HashMap<ChainHash, Transaction> tx_map, HashMap<ChainHash, Integer> tx_height_map)
  {
    AuditLogChain.Builder chain = AuditLogChain.newBuilder();
    int chain_len = 0;
    ChainHash curr = head;
    while(curr != null)
    {
      Transaction tx = tx_map.get(curr);
      if (acceptableAuditTransaction(audit_log_hash, tx_map.get(curr)))
      {
        chain_len++;
        TransactionInner inner = TransactionUtil.getInner(tx);
        AuditLogItem.Builder item = AuditLogItem.newBuilder();
        item.setTxHash(curr.getBytes());
        item.setLogMsg( inner.getExtra() );
        if (tx_height_map.containsKey(curr))
        {
          item.setConfirmedHeight(tx_height_map.get(curr));
        }
        chain.addItems(item.build());

        int acceptable_sources = 0;
        ChainHash prev = null;
        for(TransactionInput input : inner.getInputsList())
        {
          if (acceptableAuditTransaction(audit_log_hash, tx_map.get( new ChainHash( input.getSrcTxId() ) ) ))
          {
            prev =  new ChainHash( input.getSrcTxId() );
            acceptable_sources++;
          }
        }
        // Only continue if there is exactly one obvious choice
        if (acceptable_sources == 1) curr = prev;
        else curr = null;

      }
      else
      {
        curr = null;
      }

    }

    
    if (chain_len > 0) return chain.build();
    return null;
  }

  private static boolean acceptableAuditTransaction(AddressSpecHash audit_log_hash, Transaction tx)
  {
    if (tx == null) return false;
    TransactionInner inner = TransactionUtil.getInner(tx);

    // Must have exactly one output to log address
    int log_outputs = 0;
    for(TransactionOutput out : inner.getOutputsList())
    {
      if (audit_log_hash.equals(out.getRecipientSpecHash())) log_outputs++;
    }
    if (log_outputs != 1) return false;


    // Must be signed by log address
    int log_sig = 0;
    for(AddressSpec claim : inner.getClaimsList())
    {
      AddressSpecHash addr = AddressUtil.getHashForSpec(claim);
      if (addr.equals(audit_log_hash)) log_sig++;
    }

    if (log_sig != 1) return false;

    return true;

  }


}
