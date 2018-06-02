package snowblossom;

import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import snowblossom.proto.*;
import snowblossomlib.AddressSpecHash;
import snowblossomlib.AddressUtil;
import snowblossomlib.Globals;
import snowblossomlib.KeyUtil;
import snowblossomlib.TransactionBridge;
import snowblossomlib.TransactionUtil;
import snowblossomlib.Validation;


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

    builder.addKeys(snowblossomlib.KeyUtil.generateWalletStandardECKey());
    builder.addKeys(snowblossomlib.KeyUtil.generateWalletECKey("secp521r1"));
    builder.addKeys(snowblossomlib.KeyUtil.generateWalletECKey("secp384r1"));
    builder.addKeys(snowblossomlib.KeyUtil.generateWalletRSAKey(1024));
    builder.addKeys(snowblossomlib.KeyUtil.generateWalletRSAKey(2048));
    builder.addKeys(snowblossomlib.KeyUtil.generateWalletDSAKey());
    builder.addKeys(snowblossomlib.KeyUtil.generateWalletDSTU4145Key(0));
    builder.addKeys(snowblossomlib.KeyUtil.generateWalletDSTU4145Key(1));
    builder.addKeys(snowblossomlib.KeyUtil.generateWalletDSTU4145Key(7));
    builder.addKeys(snowblossomlib.KeyUtil.generateWalletDSTU4145Key(9));

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



    Transaction tx = snowblossomlib.TransactionUtil.makeTransaction(wallet, ImmutableList.of(a, b, c), address_hash, 150000, 0L);

    snowblossomlib.Validation.checkTransactionBasics(tx, false);

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

    snowblossomlib.TransactionBridge a = new snowblossomlib.TransactionBridge(address_hash, 50000);
    snowblossomlib.TransactionBridge b = new snowblossomlib.TransactionBridge(address_hash, 50000);
    snowblossomlib.TransactionBridge c = new TransactionBridge(address_hash, 50000);

    Transaction tx = TransactionUtil.makeTransaction(wallet, ImmutableList.of(a, b, c), address_hash, 150000, 0L);

    Validation.checkTransactionBasics(tx, false);

    Assert.assertEquals(2, tx.getSignaturesCount());



  }

}
