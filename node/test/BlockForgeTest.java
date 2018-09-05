package node.test;

import org.junit.Test;
import org.junit.Assert;

import java.util.List;
import snowblossom.lib.*;
import snowblossom.node.BlockForge;
import snowblossom.proto.*;

import java.util.Random;

import com.google.protobuf.ByteString;

public class BlockForgeTest
{
  private NetworkParams params = new NetworkParamsRegtest();

  @Test
  public void testCoinbaseOutputBasic()
    throws Exception
  {
    Random rnd = new Random();
    byte[] addr = new byte[Globals.ADDRESS_SPEC_HASH_LEN];
    rnd.nextBytes(addr);

    long reward = 50000007;

    List<TransactionOutput> lst = BlockForge.makeCoinbaseOutputs(params, reward, 
      SubscribeBlockTemplateRequest.newBuilder()
        .setPayRewardToSpecHash( ByteString.copyFrom(addr) ).build());

    Assert.assertEquals(1, lst.size());
    Assert.assertEquals(reward, lst.get(0).getValue());
  }

  @Test
  public void testCoinbaseOutputEvenNoRound()
    throws Exception
  {
    Random rnd = new Random();
    byte[] addr = new byte[Globals.ADDRESS_SPEC_HASH_LEN];

    for(int i=0; i<1000; i++)
    {
      int count = rnd.nextInt(50) + 2;
      SubscribeBlockTemplateRequest.Builder req = 
      SubscribeBlockTemplateRequest.newBuilder();

      double weight = rnd.nextDouble() + 0.05;

      for(int j=0; j<count; j++)
      {
        rnd.nextBytes(addr);
        
        String addr_str = AddressUtil.getAddressString( params.getAddressPrefix(), new AddressSpecHash(addr));

        req.putPayRatios(addr_str, weight);
      }

      long reward = (1+ rnd.nextInt(5000)) * count;
      List<TransactionOutput> lst = BlockForge.makeCoinbaseOutputs(params, reward, req.build());
      Assert.assertEquals(count, lst.size());
      long expected = reward / count;
      long total = 0;

      for(TransactionOutput out : lst)
      {
        total+=out.getValue();
        long diff = Math.abs(expected - out.getValue());
      }
      Assert.assertEquals(reward, total);

    }
  }

  @Test
  public void testCoinbaseOutputEvenWithRound()
    throws Exception
  {
    Random rnd = new Random();
    byte[] addr = new byte[Globals.ADDRESS_SPEC_HASH_LEN];

    for(int i=0; i<1000; i++)
    {
      int count = rnd.nextInt(50) + 2;
      SubscribeBlockTemplateRequest.Builder req = 
      SubscribeBlockTemplateRequest.newBuilder();

      double weight = rnd.nextDouble();

      for(int j=0; j<count; j++)
      {
        rnd.nextBytes(addr);
        
        String addr_str = AddressUtil.getAddressString( params.getAddressPrefix(), new AddressSpecHash(addr));

        req.putPayRatios(addr_str, weight);
      }

      long reward = (1+ rnd.nextInt(5000)) * count;
      reward += rnd.nextInt(count - 1) + 1;
      List<TransactionOutput> lst = BlockForge.makeCoinbaseOutputs(params, reward, req.build());
      Assert.assertEquals(count, lst.size());

      long total = 0;
      long expected = reward / count;

      for(TransactionOutput out : lst)
      {
        total+=out.getValue();
      }

      Assert.assertEquals(reward, total);

    }
  }

  @Test
  public void testCoinbaseOutputRandom()
    throws Exception
  {
    Random rnd = new Random();
    byte[] addr = new byte[Globals.ADDRESS_SPEC_HASH_LEN];

    for(int i=0; i<1000; i++)
    {
      int count = rnd.nextInt(50) + 2;
      SubscribeBlockTemplateRequest.Builder req = 
      SubscribeBlockTemplateRequest.newBuilder();


      for(int j=0; j<count; j++)
      {
        rnd.nextBytes(addr);
        
        String addr_str = AddressUtil.getAddressString( params.getAddressPrefix(), new AddressSpecHash(addr));

        req.putPayRatios(addr_str, rnd.nextDouble() * 5000.0);
      }

      long reward = 50000000 + rnd.nextInt(1000000);
      List<TransactionOutput> lst = BlockForge.makeCoinbaseOutputs(params, reward, req.build());

      long total = 0;

      for(TransactionOutput out : lst)
      {
        total+=out.getValue();
      }

      Assert.assertEquals(reward, total);

    }
  }



}
