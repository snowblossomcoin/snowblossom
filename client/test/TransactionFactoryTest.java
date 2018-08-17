package client.test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import snowblossom.proto.*;
import snowblossom.util.proto.*;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.Globals;
import snowblossom.lib.KeyUtil;
import snowblossom.lib.TransactionBridge;
import snowblossom.lib.TransactionUtil;
import snowblossom.lib.Validation;
import snowblossom.lib.NetworkParamsRegtest;
import snowblossom.client.WalletUtil;
import snowblossom.client.TransactionFactory;
import duckutil.ConfigMem;

import java.util.LinkedList;

import com.google.protobuf.ByteString;
public class TransactionFactoryTest
{

  @BeforeClass
  public static void loadProvider()
  {
    Globals.addCryptoProvider();
  }



  @Test
  public void multisigSeparateSigning()
    throws Exception
  {
    WalletDatabase.Builder builder = WalletDatabase.newBuilder();

    for(int i=0; i<10; i++)
    {
      builder.addKeys(KeyUtil.generateWalletStandardECKey());
    }

    AddressSpec.Builder spec = AddressSpec.newBuilder();

    for(WalletKeyPair wkp : builder.getKeysList())
    {
      spec.addSigSpecs( SigSpec.newBuilder()
        .setSignatureType(wkp.getSignatureType())
        .setPublicKey(wkp.getPublicKey())
        .build());
    }
    spec.setRequiredSigners(builder.getKeysCount());

    AddressSpec claim = spec.build();

    builder.addAddresses(claim);

    WalletDatabase big_wallet = builder.build();

    AddressSpecHash address_hash = AddressUtil.getHashForSpec(claim);

    TransactionBridge a = new TransactionBridge(address_hash, 50000);
    TransactionBridge b = new TransactionBridge(address_hash, 50000);
    TransactionBridge c = new TransactionBridge(address_hash, 50000);

    LinkedList<WalletDatabase> small_db = new LinkedList<>();
    
    for(WalletKeyPair wkp : builder.getKeysList())
    {
      small_db.add( WalletDatabase.newBuilder().addKeys(wkp).build() );
    }

    TransactionFactoryConfig.Builder tx_config = TransactionFactoryConfig.newBuilder();
    tx_config.setSign(false);
    tx_config.setInputSpecificList(true);
    tx_config.addAllInputs(ImmutableList.of(a.toUTXOEntry(), b.toUTXOEntry(), b.toUTXOEntry()));
    tx_config.setFeeFlat(100L);
    tx_config.addOutputs(TransactionOutput.newBuilder().setRecipientSpecHash( address_hash.getBytes() ).setValue(150000L-100L).build());

    TransactionFactoryResult res = TransactionFactory.createTransaction(tx_config.build(), big_wallet, null);
    Assert.assertEquals(0, res.getSignaturesAdded());
    Assert.assertFalse(res.getAllSigned());
    Assert.assertEquals(100L, res.getFee());

    for(WalletDatabase db : small_db)
    {
      Assert.assertFalse(res.getAllSigned());
      res = TransactionFactory.signTransaction(res.getTx(), db);
      Assert.assertEquals(1, res.getSignaturesAdded());
      Assert.assertEquals(100L, res.getFee());
    }
    
    Assert.assertTrue(res.getAllSigned());

    Validation.checkTransactionBasics(res.getTx(), false);

  }

}
