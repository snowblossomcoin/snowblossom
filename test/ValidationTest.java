package snowblossom;

import com.google.protobuf.ByteString;
import org.junit.BeforeClass;
import org.junit.Test;
import snowblossom.proto.*;
import lib.src.AddressSpecHash;
import lib.src.AddressUtil;
import lib.src.DigestUtil;
import lib.src.Globals;
import lib.src.KeyUtil;
import lib.src.SignatureUtil;
import lib.src.Validation;
import lib.src.ValidationException;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Random;

public class ValidationTest
{

  @BeforeClass
  public static void loadProvider()
  {
    Globals.addCryptoProvider();
  }

  @Test(expected = ValidationException.class)
  public void testAddrSpecEmptyString()
    throws Exception
  {
    byte[] empty=new byte[0];
    ByteString bs = ByteString.copyFrom(empty);

    Validation.validateAddressSpecHash(bs, "empty");
  }

  @Test(expected = ValidationException.class)
  public void testAddrSpecShortString()
    throws Exception
  {
    byte[] b=new byte[12];
    ByteString bs = ByteString.copyFrom(b);

    Validation.validateAddressSpecHash(bs, "short");
  }
  @Test(expected = ValidationException.class)
  public void testAddrSpecNullString()
    throws Exception
  {
    Validation.validateAddressSpecHash(null, "short");
  }

  @Test
  public void testAddrSpecCorrectString()
    throws Exception
  {
    byte[] b=new byte[Globals.ADDRESS_SPEC_HASH_LEN];
    ByteString bs = ByteString.copyFrom(b);

    Validation.validateAddressSpecHash(bs, "correct");
  }
 
  @Test
  public void testChainHashCorrectString()
    throws Exception
  {
    byte[] b=new byte[Globals.BLOCKCHAIN_HASH_LEN];
    ByteString bs = ByteString.copyFrom(b);

    Validation.validateChainHash(bs, "correct");
  }

  private Random rnd = new Random();

  @Test
  public void testCoinbaseTx()
    throws Exception
  {
    MessageDigest md_bc = DigestUtil.getMD();
    Transaction.Builder tx = Transaction.newBuilder();
    
    TransactionInner.Builder inner = TransactionInner.newBuilder();
    inner.setVersion(1);
    inner.setIsCoinbase(true);

    String remark = "I live in a tree";

    inner.setCoinbaseExtras( CoinbaseExtras.newBuilder()
      .setRemarks(ByteString.copyFrom(remark.getBytes()))
      .addMotionsApproved(7)
      .addMotionsRejected(91)
      .build() );

    byte[] addr = new byte[Globals.ADDRESS_SPEC_HASH_LEN];
    rnd.nextBytes(addr);

    inner.addOutputs( TransactionOutput.newBuilder()
      .setValue(50000L)
      .setRecipientSpecHash(ByteString.copyFrom(addr))
      .build());

    ByteString inner_data= inner.build().toByteString();
    tx.setInnerData(inner_data);
    tx.setTxHash(ByteString.copyFrom(md_bc.digest(inner_data.toByteArray())));

    Validation.checkTransactionBasics(tx.build(), true);

  }

  @Test
  public void testBasicTx()
    throws Exception
  {
    MessageDigest md_bc = DigestUtil.getMD();
    Transaction.Builder tx = Transaction.newBuilder();
    
    TransactionInner.Builder inner = TransactionInner.newBuilder();
    inner.setVersion(1);

    byte[] to_addr = new byte[Globals.ADDRESS_SPEC_HASH_LEN];
    rnd.nextBytes(to_addr);

    KeyPair key_pair = KeyUtil.generateECCompressedKey();


    byte[] public_key = key_pair.getPublic().getEncoded();



    byte[] src_tx = new byte[Globals.BLOCKCHAIN_HASH_LEN];
    rnd.nextBytes(src_tx);

    AddressSpec claim = AddressSpec.newBuilder()
      .setRequiredSigners(1)
      .addSigSpecs( SigSpec.newBuilder()
        .setSignatureType(SignatureUtil.SIG_TYPE_ECDSA)
        .setPublicKey(ByteString.copyFrom(public_key))
        .build())
      .build();

    AddressSpecHash addr_spec = AddressUtil.getHashForSpec(claim, DigestUtil.getMDAddressSpec());


    inner.addInputs( TransactionInput.newBuilder()
      .setSpecHash(addr_spec.getBytes())
      .setSrcTxId( ByteString.copyFrom(src_tx) )
      .setSrcTxOutIdx (1)
      .build() );
      

    inner.addOutputs( TransactionOutput.newBuilder()
      .setValue(50000L)
      .setRecipientSpecHash(ByteString.copyFrom(to_addr))
      .build());

    inner.addOutputs( TransactionOutput.newBuilder()
      .setValue(50000L)
      .setRecipientSpecHash(ByteString.copyFrom(to_addr))
      .build());
    inner.addClaims(claim);

    inner.setFee(50L);
    inner.setExtra(ByteString.copyFrom(new String("hellllo").getBytes()));

    ByteString inner_data= inner.build().toByteString();
    tx.setInnerData(inner_data);
    tx.setTxHash(ByteString.copyFrom(md_bc.digest(inner_data.toByteArray())));

    Signature sig_engine = Signature.getInstance("ECDSA");
    sig_engine.initSign(key_pair.getPrivate());
    sig_engine.update(tx.getTxHash().toByteArray());

    byte[] sig = sig_engine.sign();

    tx.addSignatures( SignatureEntry.newBuilder()
      .setClaimIdx(0)
      .setKeyIdx(0)
      .setSignature( ByteString.copyFrom(sig) )
      .build());


    Validation.checkTransactionBasics(tx.build(), false);

    System.out.println("Basic transaction size: " + tx.build().toByteString().size());

  }
 
}
