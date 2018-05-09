package snowblossom.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc;

import snowblossom.proto.GetUTXONodeReply;
import snowblossom.proto.GetUTXONodeRequest;

import snowblossom.proto.SubmitReply;
import snowblossom.proto.TransactionOutput;
import snowblossom.trie.proto.TrieNode;
import snowblossom.NetworkParams;
import snowblossom.AddressSpecHash;
import snowblossom.HexUtil;
import snowblossom.KeyUtil;
import snowblossom.SignatureUtil;
import snowblossom.AddressUtil;
import snowblossom.proto.AddressSpec;
import snowblossom.proto.SigSpec;
import snowblossom.proto.Transaction;
import java.security.KeyPair;


import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Random;
import snowblossom.Globals;
import snowblossom.SnowMerkleProof;
import snowblossom.trie.HashUtils;
import snowblossom.Config;
import snowblossom.ConfigFile;
import com.google.protobuf.ByteString;
import java.security.Security;
import java.io.File;

import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import snowblossom.TransactionBridge;

public class SnowBlossomClient
{
  private static final Logger logger = Logger.getLogger("SnowBlossomClient");

  public static void main(String args[]) throws Exception
  {
    Globals.addCryptoProvider();
    if (args.length != 1)
    {
      logger.log(Level.SEVERE, "Incorrect syntax. Syntax: SnowBlossomClient <config_file>");
      System.exit(-1);
    }

    ConfigFile config = new ConfigFile(args[0]);


    new SnowBlossomClient(config);
  }


  private final UserServiceStub asyncStub;
  private final UserServiceBlockingStub blockingStub;

	private final NetworkParams params;

  public SnowBlossomClient(Config config) throws Exception
  {
    config.require("node_host");

    String host = config.get("node_host");
    int port = config.getIntWithDefault("node_port", 2338);

    params = NetworkParams.loadFromConfig(config);
		
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();

    asyncStub = UserServiceGrpc.newStub(channel);
    blockingStub = UserServiceGrpc.newBlockingStub(channel);

    /*Random rnd = new Random();

    byte[] buff=new byte[20];
    rnd.nextBytes(buff);
    for(int i=0; i<256;i++)
    {
      TreeMap<String, Integer> m=new TreeMap<>();
      for(int j=0; j<100000; j++)
      {
        rnd.nextBytes(buff);

        String start =  org.bitcoinj.core.AddrBridge.encode(i, buff).substring(0,1);

        if (!m.containsKey(start))
        {
          m.put(start,0);
        }
        m.put(start, m.get(start) + 1);

  
      }
      System.out.println("" + i + ": " + m);
    }*/
  }

  public List<TransactionBridge> getSpendable(AddressSpecHash addr)
  {

    GetUTXONodeReply reply = blockingStub.getUTXONode( GetUTXONodeRequest.newBuilder()
      .setPrefix(addr.getBytes())
      .setIncludeProof(true)
      .build());
    LinkedList<TransactionBridge> out_list = new LinkedList<>();
    for(TrieNode node : reply.getAnswerList())
    {
      if (node.getIsLeaf())
      {
        TransactionBridge b = new TransactionBridge(node);

        out_list.add(b);
      }
    }

    return out_list;
  }

  public boolean submitTransaction(Transaction tx)
  {
    SubmitReply reply = blockingStub.submitTransaction(tx);

    return reply.getSuccess();
  }


}
