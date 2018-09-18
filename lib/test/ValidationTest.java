package lib.test;

import com.google.protobuf.ByteString;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assert;
import snowblossom.proto.*;
import snowblossom.lib.*;

import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Random;
import java.util.ArrayList;
import com.google.common.collect.ImmutableList;
import java.util.List;

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

    crossCheckTxOut(tx.build());
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

    crossCheckTxOut(tx.build());

    System.out.println("Basic transaction size: " + tx.build().toByteString().size());
  }


  @Test
  public void testTxOutCoding()
    throws Exception
  {
    Random rnd = new Random();
    // Try a variety of values and make sure they all encode right
    List<Long> values = ImmutableList.of(0L, 9101L, 1000000000L, 180000101L);
    List<Integer> lengths = ImmutableList.of(0, 16, 32, 1001, 65010, 134111);

    for(long val : values)
    for(int byte_size : lengths)
    {
      System.out.println("Checking output val: " + val + " addr size: " + byte_size);
      Transaction.Builder tx = Transaction.newBuilder();
      TransactionInner.Builder inner = TransactionInner.newBuilder();

      for(int i=0; i<3; i++)
      {
        byte[] b = new byte[byte_size];
        rnd.nextBytes(b);
        ByteString bs = ByteString.copyFrom(b);
        inner.addOutputs( TransactionOutput.newBuilder().setValue(val).setRecipientSpecHash(bs).build() );
      }

      tx.setInnerData( inner.build().toByteString() );
      crossCheckTxOut( tx.build() );

    }

  }

  private void crossCheckTxOut(Transaction tx)
    throws Exception
  {
    ArrayList<ByteString> tx_out_raw = TransactionUtil.extractWireFormatTxOut(tx);
    TransactionInner inner = TransactionUtil.getInner(tx);

    Assert.assertEquals(tx_out_raw.size(), inner.getOutputsCount());

    for(int i=0; i<tx_out_raw.size(); i++)
    {
      String raw = HexUtil.getHexString(tx_out_raw.get(i));
      String recode = HexUtil.getHexString(inner.getOutputs(i).toByteString());

      Assert.assertEquals(raw, recode);
      Assert.assertEquals(tx_out_raw.get(i), inner.getOutputs(i).toByteString());

      TransactionOutput p_tx_out_raw = TransactionOutput.parseFrom(tx_out_raw.get(i));
      TransactionOutput p_tx_out_recode = TransactionOutput.parseFrom(inner.getOutputs(i).toByteString());

      Assert.assertEquals(p_tx_out_raw, p_tx_out_recode);
    }
  }

  @Test(expected = ValidationException.class)
  public void testRequirementTimeBeforeHeight()
    throws Exception
  {
    TransactionOutput out = TransactionOutput.newBuilder()
      .setRequirements(
        TransactionRequirements.newBuilder().setRequiredTime(System.currentTimeMillis() + 3600L).build())
      .build();

    NetworkParams params = new NetworkParamsRegtest();
    BlockHeader header = BlockHeader.newBuilder()
      .setBlockHeight( params.getActivationHeightTxOutRequirements() - 1)
      .setTimestamp( System.currentTimeMillis() )
      .build();

    Validation.validateTransactionOutput(out, header, params);
  }

  @Test(expected = ValidationException.class)
  public void testRequirementBlockBeforeHeight()
    throws Exception
  {
    TransactionOutput out = TransactionOutput.newBuilder()
      .setRequirements(
        TransactionRequirements.newBuilder().setRequiredBlockHeight(8000).build())
      .build();

    NetworkParams params = new NetworkParamsRegtest();
    BlockHeader header = BlockHeader.newBuilder()
      .setBlockHeight( params.getActivationHeightTxOutRequirements() - 1)
      .setTimestamp( System.currentTimeMillis() )
      .build();

    Validation.validateTransactionOutput(out, header, params);
  }

  @Test
  public void testRequirementTimeAtHeight()
    throws Exception
  {
    TransactionOutput out = TransactionOutput.newBuilder()
      .setRequirements(
        TransactionRequirements.newBuilder().setRequiredTime(System.currentTimeMillis() + 3600L).build())
      .build();

    NetworkParams params = new NetworkParamsRegtest();
    BlockHeader header = BlockHeader.newBuilder()
      .setBlockHeight( params.getActivationHeightTxOutRequirements())
      .setTimestamp( System.currentTimeMillis() )
      .build();

    Validation.validateTransactionOutput(out, header, params);
  }

  @Test
  public void testRequirementBlockAtHeight()
    throws Exception
  {
    TransactionOutput out = TransactionOutput.newBuilder()
      .setRequirements(
        TransactionRequirements.newBuilder().setRequiredBlockHeight(8000).build())
      .build();

    NetworkParams params = new NetworkParamsRegtest();
    BlockHeader header = BlockHeader.newBuilder()
      .setBlockHeight( params.getActivationHeightTxOutRequirements())
      .setTimestamp( System.currentTimeMillis() )
      .build();

    Validation.validateTransactionOutput(out, header, params);
  }

  @Test
  public void testExtrasAtHeight()
    throws Exception
  {
    byte[] b=new byte[20];
    ByteString bs = ByteString.copyFrom(b);
    TransactionOutput out = TransactionOutput.newBuilder()
      .setForBenefitOfSpecHash(bs)
      .build();

    NetworkParams params = new NetworkParamsRegtest();
    BlockHeader header = BlockHeader.newBuilder()
      .setBlockHeight( params.getActivationHeightTxOutExtras())
      .setTimestamp( System.currentTimeMillis() )
      .build();

    Validation.validateTransactionOutput(out, header, params);
  }

  @Test(expected = ValidationException.class)
  public void testExtrasBeforeHeight()
    throws Exception
  {
    byte[] b=new byte[20];
    ByteString bs = ByteString.copyFrom(b);
    TransactionOutput out = TransactionOutput.newBuilder()
      .setForBenefitOfSpecHash(bs)
      .build();

    NetworkParams params = new NetworkParamsRegtest();
    BlockHeader header = BlockHeader.newBuilder()
      .setBlockHeight( params.getActivationHeightTxOutExtras() - 1)
      .setTimestamp( System.currentTimeMillis() )
      .build();

    Validation.validateTransactionOutput(out, header, params);
  }

  @Test
  public void testSpendableAtHeight()
    throws Exception
  {
    TransactionOutput out = TransactionOutput.newBuilder()
      .setRequirements(
        TransactionRequirements.newBuilder().setRequiredBlockHeight(8000).build())
      .build();

    NetworkParams params = new NetworkParamsRegtest();
    BlockHeader header = BlockHeader.newBuilder()
      .setBlockHeight( 8000 )
      .setTimestamp( System.currentTimeMillis() )
      .build();

    Validation.validateSpendable(out, header, params);
  }

  @Test(expected = ValidationException.class)
  public void testNotSpendableAtHeight()
    throws Exception
  {
    TransactionOutput out = TransactionOutput.newBuilder()
      .setRequirements(
        TransactionRequirements.newBuilder().setRequiredBlockHeight(8000).build())
      .build();

    NetworkParams params = new NetworkParamsRegtest();
    BlockHeader header = BlockHeader.newBuilder()
      .setBlockHeight( 7999 )
      .setTimestamp( System.currentTimeMillis() )
      .build();

    Validation.validateSpendable(out, header, params);
  }


  @Test
  public void testSpendableAtTime()
    throws Exception
  {
    TransactionOutput out = TransactionOutput.newBuilder()
      .setRequirements(
        TransactionRequirements.newBuilder().setRequiredTime(System.currentTimeMillis()).build())
      .build();

    NetworkParams params = new NetworkParamsRegtest();
    BlockHeader header = BlockHeader.newBuilder()
      .setBlockHeight( 8000 )
      .setTimestamp( System.currentTimeMillis() )
      .build();

    Validation.validateSpendable(out, header, params);
  }

  @Test(expected = ValidationException.class)
  public void testNotSpendableAtTime()
    throws Exception
  {
    long tm = System.currentTimeMillis();
    TransactionOutput out = TransactionOutput.newBuilder()
      .setRequirements(
        TransactionRequirements.newBuilder().setRequiredTime(tm).build())
      .build();

    NetworkParams params = new NetworkParamsRegtest();
    BlockHeader header = BlockHeader.newBuilder()
      .setBlockHeight( 8000 )
      .setTimestamp( tm - 100 )
      .build();

    Validation.validateSpendable(out, header, params);
  }





}
