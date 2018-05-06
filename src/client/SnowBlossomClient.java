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
    config.require("snow_path");
    config.require("node_host");

    String host = config.get("node_host");
    int port = config.getIntWithDefault("node_port", 2338);

    params = NetworkParams.loadFromConfig(config);
		
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();

    asyncStub = UserServiceGrpc.newStub(channel);
    blockingStub = UserServiceGrpc.newBlockingStub(channel);

    Random rnd = new Random();

    KeyPair key_pair = KeyUtil.generateECCompressedKey();
		


      AddressSpec claim = AddressSpec.newBuilder()
        .setRequiredSigners(1)
        .addSigSpecs( SigSpec.newBuilder()
          .setSignatureType( SignatureUtil.SIG_TYPE_ECDSA)
          .setPublicKey(ByteString.copyFrom(key_pair.getPublic().getEncoded()))
          .build())
        .build();

		AddressSpecHash to_addr = AddressUtil.getHashForSpec(claim);


    System.out.println("Address: " + to_addr);


    while(true)
    {
			LinkedList<TransactionOutput> out_list = new LinkedList<>();

      GetUTXONodeReply reply = blockingStub.getUTXONode( GetUTXONodeRequest.newBuilder()
        .setPrefix(to_addr.getBytes())
        .setIncludeProof(true)
        .build());

      long total = 0;
      int outputs = 0;
      for(TrieNode node : reply.getAnswerList())
      {
        if (node.getIsLeaf())
        {
          TransactionOutput out = TransactionOutput.parseFrom(node.getLeafData());
          total += out.getValue();
          outputs++;
					out_list.add(out);
        }
      }

      System.out.println("Total value: " + total + " in " + outputs + " outputs");

      Thread.sleep(15000);
    }


  }

}
