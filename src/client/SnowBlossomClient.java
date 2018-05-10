package snowblossom.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import snowblossom.proto.UserServiceGrpc.UserServiceStub;
import snowblossom.proto.UserServiceGrpc.UserServiceBlockingStub;
import snowblossom.proto.UserServiceGrpc;

import snowblossom.proto.GetUTXONodeReply;
import snowblossom.proto.GetUTXONodeRequest;
import org.junit.Assert;
import java.text.DecimalFormat;

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
import snowblossom.proto.WalletKeyPair;
import snowblossom.proto.WalletDatabase;

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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

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

  private File wallet_path;
  private WalletDatabase wallet_database;

  public SnowBlossomClient(Config config) throws Exception
  {
    config.require("node_host");

    String host = config.get("node_host");
    params = NetworkParams.loadFromConfig(config);
    int port = config.getIntWithDefault("node_port", params.getDefaultPort());

		
    ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();

    asyncStub = UserServiceGrpc.newStub(channel);
    blockingStub = UserServiceGrpc.newBlockingStub(channel);

    if (config.isSet("wallet_path"))
    {
      wallet_path = new File(config.get("wallet_path"));
      loadWallet();
      showBalances();
    }

  }

  public void loadWallet()
    throws Exception
  {
    wallet_path.mkdirs();

    File db_file = new File(wallet_path, "wallet.db");
    if (db_file.exists())
    {
      wallet_database = WalletDatabase.parseFrom(new FileInputStream(db_file));

    }
    else
    {
      logger.log(Level.WARNING, String.format("File %s does not exist, creating new wallet", db_file.getPath()));
      wallet_database = makeNewDatabase();
      saveWallet();
    }

  }

  public void saveWallet()
    throws Exception
  {
    File db_file_tmp = new File(wallet_path, ".wallet.db.tmp");
    db_file_tmp.delete();

    FileOutputStream out = new FileOutputStream(db_file_tmp, false);
    wallet_database.writeTo(out);
    out.flush();
    out.close();
    WalletDatabase read = WalletDatabase.parseFrom(new FileInputStream(db_file_tmp));

    Assert.assertEquals(wallet_database, read);
    File db_file = new File(wallet_path, "wallet.db");

    if (db_file_tmp.renameTo(db_file))
    {
      logger.log(Level.INFO, String.format("Save to file %s completed", db_file.getPath()));
    } 
    else
    {
      String msg = String.format("Unable to rename tmp file %s to %s", db_file_tmp.getPath(), db_file.getPath());

      logger.log(Level.WARNING, msg);
      throw new IOException(msg);
    }


  }

  public WalletDatabase makeNewDatabase()
  {
    WalletDatabase.Builder builder = WalletDatabase.newBuilder();

    for(int i=0;i<8; i++)
    {
      genNewKey(builder);
    }

    return builder.build();
  }

  public void genNewKey(WalletDatabase.Builder wallet_builder)
  {
    KeyPair key_pair = KeyUtil.generateECCompressedKey();

    ByteString public_encoded = KeyUtil.getCompressedPublicKeyEncoding(key_pair.getPublic());

    WalletKeyPair wkp = WalletKeyPair.newBuilder()
       .setPublicKey(KeyUtil.getCompressedPublicKeyEncoding(key_pair.getPublic()))
       .setPrivateKey(ByteString.copyFrom(key_pair.getPrivate().getEncoded()))
       .setSignatureType(SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED)
       .build();
  

    wallet_builder.addKeys(wkp);

    AddressSpec claim = AddressUtil.getSimpleSpecForKey(key_pair.getPublic(), SignatureUtil.SIG_TYPE_ECDSA_COMPRESSED);

    wallet_builder.addAddresses(claim);

  } 

  public void showBalances()
  {
    for(AddressSpec claim : wallet_database.getAddressesList())
    {
      AddressSpecHash hash = AddressUtil.getHashForSpec(claim);
      String address = AddressUtil.getAddressString(params.getAddressPrefix(), hash);
      System.out.print("Address: " + address + " - ");
      List<TransactionBridge> bridges = getSpendable(hash);

      long value = 0;
      for(TransactionBridge b : bridges)
      {
        value += b.value;
      }
      double val_d = (double) value / (double) Globals.SNOW_VALUE;
      DecimalFormat df = new DecimalFormat("0.000000");
      System.out.println(String.format(" %s in %d outputs", df.format(val_d), bridges.size()));
      
    }
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
