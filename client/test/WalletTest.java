package client.test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import snowblossom.proto.*;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;
import snowblossom.lib.Globals;
import snowblossom.lib.KeyUtil;
import snowblossom.lib.TransactionBridge;
import snowblossom.lib.TransactionUtil;
import snowblossom.lib.Validation;
import snowblossom.lib.NetworkParamsRegtest;
import snowblossom.client.WalletUtil;
import duckutil.ConfigMem;

import com.google.protobuf.ByteString;
public class WalletTest
{

  @BeforeClass
  public static void loadProvider()
  {
    Globals.addCryptoProvider();
  }



  @Test
  public void multisigAllInTest()
    throws Exception
  {
    WalletDatabase.Builder builder = WalletDatabase.newBuilder();

    builder.addKeys(KeyUtil.generateWalletStandardECKey());
    builder.addKeys(KeyUtil.generateWalletECKey("secp521r1"));
    builder.addKeys(KeyUtil.generateWalletECKey("secp384r1"));
    builder.addKeys(KeyUtil.generateWalletRSAKey(1024));
    builder.addKeys(KeyUtil.generateWalletRSAKey(2048));
    builder.addKeys(KeyUtil.generateWalletDSAKey());
    builder.addKeys(KeyUtil.generateWalletDSTU4145Key(0));
    builder.addKeys(KeyUtil.generateWalletDSTU4145Key(1));
    builder.addKeys(KeyUtil.generateWalletDSTU4145Key(7));
    builder.addKeys(KeyUtil.generateWalletDSTU4145Key(9));

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

    WalletDatabase wallet = builder.build();

    AddressSpecHash address_hash = AddressUtil.getHashForSpec(claim);

    TransactionBridge a = new TransactionBridge(address_hash, 50000);
    TransactionBridge b = new TransactionBridge(address_hash, 50000);
    TransactionBridge c = new TransactionBridge(address_hash, 50000);



    Transaction tx = TransactionUtil.makeTransaction(wallet, ImmutableList.of(a, b, c), address_hash, 150000, 0L);

    Validation.checkTransactionBasics(tx, false);

    Assert.assertEquals(wallet.getKeysCount(), tx.getSignaturesCount());


  }

  @Test
  public void multisigNofM()
    throws Exception
  {
    WalletDatabase.Builder builder = WalletDatabase.newBuilder();

    for(int i=0; i<3; i++)
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
    spec.setRequiredSigners(2);

    AddressSpec claim = spec.build();

    builder.addAddresses(claim);

    WalletDatabase wallet = builder.build();

    AddressSpecHash address_hash = AddressUtil.getHashForSpec(claim);

    TransactionBridge a = new TransactionBridge(address_hash, 50000);
    TransactionBridge b = new TransactionBridge(address_hash, 50000);
    TransactionBridge c = new TransactionBridge(address_hash, 50000);

    Transaction tx = TransactionUtil.makeTransaction(wallet, ImmutableList.of(a, b, c), address_hash, 150000, 0L);

    Validation.checkTransactionBasics(tx, false);

    Assert.assertEquals(2, tx.getSignaturesCount());
  }

  @Test
  public void testWalletSerialize()
    throws Exception
  {
    ConfigMem config = new ConfigMem(ImmutableMap.of());

    WalletDatabase.Builder builder = WalletDatabase.newBuilder();
    WalletUtil.genNewKey(WalletDatabase.newBuilder().build(), builder, config, new NetworkParamsRegtest()); 

    WalletDatabase a = builder.build();

    ByteString str = a.toByteString();

    WalletDatabase b = WalletDatabase.parseFrom(str);

    Assert.assertEquals(a,b);
  }
}
