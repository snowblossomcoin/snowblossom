package snowblossom.lib;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.*;
import org.junit.Assert;
import snowblossom.lib.trie.HashedTrie;
import snowblossom.lib.trie.TrieDBMem;
import snowblossom.proto.*;

/**
 * This is the heart of a cryptocurrency.  The block validation.
 * The network peers can all scream lies at each other.
 * The miners could be tricksters running elaborate scams.
 * Here is where the truth comes out.
 * As long as this is correct, nothing else can be that bad.
 */
public class Validation
{
  public static void checkBlockHeaderBasics(NetworkParams params, BlockHeader header, boolean ignore_target)
    throws ValidationException
  {
    Block blk = Block.newBuilder().setHeader(header).build();

    checkBlockBasics(params, blk, false, ignore_target);

  }

  /**
   * Check the things about a block that can be checked without the database
   */
  public static void checkBlockBasics(NetworkParams params, Block blk, boolean require_transactions, boolean ignore_target)
    throws ValidationException
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("Validation.checkBlockBasics"))
    {
      BlockHeader header = blk.getHeader();
      if (header == null) throw new ValidationException("Header missing");

      if ((header.getVersion() != 1) && (header.getVersion() != 2))
      {
        throw new ValidationException(String.format("Unknown block version: %d", header.getVersion()));
      }
      if (header.getBlockHeight() < params.getActivationHeightShards())
      {
        if (header.getVersion() != 1)
        {
          throw new ValidationException(String.format("Block version must be 1 before shard activation"));
        }
      }
      else
      {
        if (header.getVersion() != 2)
        {
          throw new ValidationException("Block version must be 2 after shard activation");

        }
      }

      if (header.getTimestamp() > System.currentTimeMillis() + params.getMaxClockSkewMs())
      {
        throw new ValidationException("Block too far into future");
      }

      validateChainHash(header.getPrevBlockHash(), "prev_block_hash");
      validateChainHash(header.getMerkleRootHash(), "merkle_root_hash");
      validateChainHash(header.getUtxoRootHash(), "utxo_root_hash");
      validateChainHash(header.getSnowHash(), "snow_hash");

      validateByteString(header.getNonce(), "nonce", Globals.NONCE_LENGTH);
      validateByteString(header.getTarget(), "target", Globals.TARGET_LENGTH);

      SnowFieldInfo field_info = params.getSnowFieldInfo(header.getSnowField());
      if (field_info == null)
      {
        throw new ValidationException("Unknown snow field");
      }


      LinkedList<SnowPowProof> proofs = new LinkedList<>();
      proofs.addAll(header.getPowProofList());

      if (proofs.size() != Globals.POW_LOOK_PASSES)
      {
        throw new ValidationException("Wrong number of POW passes");
      }

      //check pow proofs
      for(SnowPowProof proof : proofs)
      {
        if (!checkProof(proof, field_info.getMerkleRootHash(), field_info.getLength()))
        {
          throw new ValidationException("POW Merkle Proof does not compute");
        }
      }
      
      //make sure pow proofs lead to snow hash
      byte[] pass_one = PowUtil.hashHeaderBits(header, header.getNonce().toByteArray());
      byte[] context = pass_one;
      long word_count = field_info.getLength() / (long)Globals.SNOW_MERKLE_HASH_LEN;

      for(SnowPowProof proof : proofs)
      {
        long idx = proof.getWordIdx();
        long nx = PowUtil.getNextSnowFieldIndex(context, word_count);
        if (idx != nx)
        {
          throw new ValidationException(String.format("POW Pass index does not match %d %d %d", idx, nx, word_count));
        }
        byte[] data = proof.getMerkleComponentList().get(0).toByteArray();
        context = PowUtil.getNextContext(context, data);
      }

      ByteString block_hash = ByteString.copyFrom(context);
      if (!header.getSnowHash().equals(block_hash))
      {
        throw new ValidationException("POW Hash does not match");
      }

      if (header.getVersion() == 1)
      {
        if (header.getShardId() != 0)
        {
          throw new ValidationException("Header version 1 must not have shard id");
        }
        if (header.getShardExportRootHashMap().size() != 0)
        {
          throw new ValidationException("Header version 1 must not have export map");
        }
        if (header.getShardImportMap().size() != 0)
        {
          throw new ValidationException("Header version 1 must not have shard import map");
        }   

      }
      else if (header.getVersion() == 2)
      {

        int my_shard_id = header.getShardId();
        Set<Integer> my_cover_set = ShardUtil.getCoverSet(my_shard_id, params);

        for(Map.Entry<Integer, ByteString> me : header.getShardExportRootHashMap().entrySet())
        {
          int export_shard_id = me.getKey();
          if (my_cover_set.contains(export_shard_id))
          {
            throw new ValidationException("Has shard_export_root_hash for self");
          }
          validateChainHash( me.getValue(), "shard_export_root_hash utxo for " + export_shard_id); 
        }

        for(int import_shard_id : header.getShardImportMap().keySet())
        {
          if (my_cover_set.contains(import_shard_id))
          {
            throw new ValidationException(String.format("Import for shard from cover set.  Importing %d into %d",import_shard_id, my_shard_id));
          }

          BlockImportList bil = header.getShardImportMap().get(import_shard_id);
          for(int import_height : bil.getHeightMap().keySet())
          {
            validateNonNegValue(import_shard_id, "import_shard_id");
            validateNonNegValue(import_height, "import_height");
            validateChainHash( bil.getHeightMap().get(import_height), "shard_import_blocks");
          }
        }

        validatePositiveValue(header.getTxDataSizeSum(), "tx_data_size_sum");
      }

      if (!ignore_target)
      {
        if (!PowUtil.lessThanTarget(context, header.getTarget()))
        {
          throw new ValidationException("Hash not less than target");
        }
      }
      
      //if we has transactions, make sure the each validate and merkle to merkle_root_hash
      if ((require_transactions) || (blk.getTransactionsCount() > 0))
      {
        if (blk.getTransactionsCount() < 1)
        {
          throw new ValidationException("Must be at least one transaction in a block");
        }

        ArrayList<ChainHash> tx_list = new ArrayList<>();
        for(int i=0; i<blk.getTransactionsCount(); i++)
        {
          Transaction tx = blk.getTransactions(i);
          boolean coinbase = false;
          if (i == 0) coinbase = true;
          checkTransactionBasics(tx, coinbase);

          tx_list.add(new ChainHash(tx.getTxHash()));
        }

        ChainHash merkle_root = DigestUtil.getMerkleRootForTxList(tx_list);
        if (!merkle_root.equals(header.getMerkleRootHash()))
        {
          throw new ValidationException(String.format("MerkleRootHash mismatch.  Found: %s, Block has: %s",
            merkle_root.toString(),
            new ChainHash(header.getMerkleRootHash()).toString()));
        }
      }
    }

  }

  public static boolean checkProof(SnowPowProof proof, ByteString expected_merkle_root, long snow_field_size)
  {
    long target_index = proof.getWordIdx();
    long word_count = snow_field_size / SnowMerkle.HASH_LEN_LONG;
    if (target_index < 0) return false;
    if (target_index >= word_count) return false;

    MessageDigest md;
    try
    {
      md = MessageDigest.getInstance(Globals.SNOW_MERKLE_HASH_ALGO);
    }
    catch(java.security.NoSuchAlgorithmException e)
    {
      throw new RuntimeException( e );
    }

    LinkedList<ByteString> stack = new LinkedList<>();
    stack.addAll(proof.getMerkleComponentList());

    ByteString current_hash = stack.poll();
    if (current_hash == null) return false;

    long start = target_index;
    long end = target_index;
    long dist = 1;

    // To visualize this, take the recursive getInnerProof() below
    // and do it backwards
    while ((stack.size() > 0) && (end <= word_count))
    {
      dist *= 2;
      start = start - (start % dist);
      end = start + dist;
      long mid = (start + end) / 2;

      ByteString left = null;
      ByteString right = null;
      if (target_index < mid)
      {
        left = current_hash;
        right = stack.poll();
      }
      else
      {
        left = stack.poll();
        right = current_hash;
      }

      md.update(left.toByteArray());
      md.update(right.toByteArray());

      byte[] hash = md.digest();
      current_hash = ByteString.copyFrom(hash);

    }

    // We expect to end with our sublist being the entire file
    // so we check to avoid someone passing off an intermediate node
    // as a merkle tree member
    if (start != 0) return false;
    if (end != word_count) return false;

    if (current_hash.equals(expected_merkle_root)) return true;

    return false;
  }

  public static void deepBlockValidation(NetworkParams params, HashedTrie utxo_hashed_trie, Block blk, BlockSummary prev_summary)
    throws ValidationException
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("Validation.deepBlockValidation"))
    {
      //Check expected target
      BigInteger expected_target = PowUtil.calcNextTarget(prev_summary, params, blk.getHeader().getTimestamp());
      ByteString expected_target_bytes = BlockchainUtil.targetBigIntegerToBytes(expected_target);

      if (!blk.getHeader().getTarget().equals(expected_target_bytes))
      {
        throw new ValidationException("Block target does not match expected target");
      }

      if (blk.getHeader().getSnowField() < prev_summary.getActivatedField())
      {
        throw new ValidationException(String.format("Snow field %d when at least %d is required", 
          blk.getHeader().getSnowField(), 
          prev_summary.getActivatedField()));
      }


      // Check timestamps and block height
      ChainHash prevblock = new ChainHash(blk.getHeader().getPrevBlockHash());

      if (prevblock.equals(ChainHash.ZERO_HASH))
      {
        if (blk.getHeader().getBlockHeight() != 0)
        {
          throw new ValidationException("Block height must be zero for first block");
        }
      }
      else
      {
        if (prev_summary.getHeader().getBlockHeight() + 1 != blk.getHeader().getBlockHeight())
        {
          throw new ValidationException("Block height must not be prev block plus one");
        }
        if (prev_summary.getHeader().getTimestamp() >= blk.getHeader().getTimestamp())
        {
          throw new ValidationException("Block time must be greater than last one");
        }
      }


      // At this point, we have a block with a reasonable header that matches everything
      // (pow, target, merkle root, etc).
      // now have to check the following
      // - coinbase tx height correct
      // - coinbase tx remark correct if first block
      // - For each transaction
      //   - transaction inputs exist in utxo
      //   - sum of inputs = sum of outputs + fee
      // - sum of coinbase output = block reward plus fee sum
      // - new UTXO root is what is expected

      Transaction coinbase_tx = blk.getTransactions(0);
      TransactionInner coinbase_inner = null;

      try
      {
        coinbase_inner = TransactionInner.parseFrom(coinbase_tx.getInnerData());
      }
      catch(java.io.IOException e)
      {
        throw new ValidationException("error parsing coinbase on second pass somehow", e);
      }
      
      if (coinbase_inner.getCoinbaseExtras().getBlockHeight() != blk.getHeader().getBlockHeight())
      {
        throw new ValidationException("Block height in block header does not match block height in coinbase");
      }
      if (coinbase_inner.getCoinbaseExtras().getShardId() != blk.getHeader().getShardId())
      {
        throw new ValidationException("Block shard_id in block header does not match shard_id in coinbase");
      }
      if (blk.getHeader().getBlockHeight() == 0)
      {
        if (!coinbase_inner.getCoinbaseExtras().getRemarks().startsWith( params.getBlockZeroRemark()))
        {
          throw new ValidationException("Block zero remark must start with defined remark");
        }
      }

      UtxoUpdateBuffer utxo_buffer = new UtxoUpdateBuffer(utxo_hashed_trie, 
        new ChainHash(prev_summary.getHeader().getUtxoRootHash()));

      if (blk.getHeader().getVersion() == 2)
      {
        checkShardBasics(blk, prev_summary, params);

        if (shouldResetUtxo(blk, prev_summary, params))
        {
          utxo_buffer = new UtxoUpdateBuffer( utxo_hashed_trie, UtxoUpdateBuffer.EMPTY );
        }

        // Add in imported outputs
        for(ImportedBlock ib : blk.getImportedBlocksList())
        {
          validateShardImport(params, ib, blk.getHeader().getShardId(), utxo_buffer);
        }
      }

      long fee_sum = 0L;
      long tx_size_sum = 0L;

      Set<Integer> cover_set = ShardUtil.getCoverSet( blk.getHeader().getShardId(), params );
      Map<Integer, UtxoUpdateBuffer> export_utxo_buffer = new TreeMap<>();

      for(Transaction tx : blk.getTransactionsList())
      {
        fee_sum += deepTransactionCheck(tx, utxo_buffer, blk.getHeader(), params, cover_set, export_utxo_buffer);
        tx_size_sum += tx.getInnerData().size() + tx.getTxHash().size();
      }

      // Check export set
      if (!export_utxo_buffer.keySet().equals( blk.getHeader().getShardExportRootHashMap().keySet()))
      {
        throw new ValidationException("Export set mismatch");
      }
      for(int export_shard : export_utxo_buffer.keySet())
      {
        ChainHash tx_export_hash = export_utxo_buffer.get(export_shard).simulateUpdates();
        ChainHash header_export_hash = new ChainHash(
          blk.getHeader().getShardExportRootHashMap().get(export_shard));
        if (!tx_export_hash.equals(header_export_hash))
        {
          throw new ValidationException("Export set utxo hash mismatch");
        }
      }

      if (blk.getHeader().getVersion() == 2)
      {
        if (blk.getHeader().getTxDataSizeSum() != tx_size_sum)
        {
          throw new ValidationException("tx_data_size_sum mismatch");
        }
      }

      long reward = ShardUtil.getBlockReward(params, blk.getHeader());
      long coinbase_sum = fee_sum + reward;

      long coinbase_spent = 0L;
      for(TransactionOutput out : coinbase_inner.getOutputsList())
      {
        coinbase_spent += out.getValue();
      }
      if (coinbase_sum != coinbase_spent)
      {
        throw new ValidationException(String.format("Coinbase could have spent %d but spent %d", coinbase_sum, coinbase_spent));
      }

      utxo_buffer.commitIfEqual(blk.getHeader().getUtxoRootHash());
    }
  
  }


  public static boolean shouldResetUtxo(Block blk, BlockSummary prev_summary, NetworkParams params)
  {
    return (ShardUtil.getShardChildIdRight(prev_summary.getHeader().getShardId()) == blk.getHeader().getShardId());
  }


  public static void checkShardBasics(Block blk, BlockSummary prev_summary, NetworkParams params)
    throws ValidationException
  {

    BlockHeader header = blk.getHeader();

    // shard id is same as prev block unless it was a split
    boolean shard_split=false;

    if (ShardUtil.shardSplit(prev_summary, params))
    {
      if (header.getShardId() == prev_summary.getHeader().getShardId())
      {
        throw new ValidationException("Must split");
      }
      if (ShardUtil.getShardParentId(header.getShardId()) != prev_summary.getHeader().getShardId())
      {
        throw new ValidationException("Shard split with wrong child");
      }
      shard_split=true;
    }
    else
    {
      if(prev_summary.getHeader().getShardId() != header.getShardId())
      {
        throw new ValidationException("Shard id should have been same as parent block");
      }
    }

    // Check block basics of imported blocks
    for(ImportedBlock ib : blk.getImportedBlocksList())
    {
      checkBlockHeaderBasics(params,ib.getHeader(), false);
    }
    
    // Check block import order
    ArrayList<String> expected_list = new ArrayList<>();

    for(int import_shard_id : PowUtil.inOrder(header.getShardImportMap().keySet()))
    {
      BlockImportList bil = header.getShardImportMap().get(import_shard_id);
      for(int import_height : PowUtil.inOrder(bil.getHeightMap().keySet()))
      {
        ChainHash hash = new ChainHash(bil.getHeightMap().get(import_height));
        expected_list.add("" + import_shard_id + "," + import_height + "," + hash.toString());
      }
    }

    if (blk.getImportedBlocksList().size() != expected_list.size())
    {
      throw new ValidationException("Mismatch of imported block list and header import list size");
    }
    for(int i=0; i<expected_list.size(); i++)
    {
      String expected = expected_list.get(i);
      ImportedBlock ib = blk.getImportedBlocks(i);
      String key = "" + ib.getHeader().getShardId() + "," + ib.getHeader().getBlockHeight() 
        + "," + new ChainHash(ib.getHeader().getSnowHash()).toString();
      if (!expected.equals(key))
      {
        throw new ValidationException("Mismatch of imported block list and header import list");
      }
    }

    // Model these blocks into our view of the chains
    TreeMap<Integer, BlockHeader> shard_friends = new TreeMap<>();

    // Put all previous heads on the friends map
    shard_friends.putAll( prev_summary.getImportedShardsMap() );

    // put ourself in
    shard_friends.put( header.getShardId(), header );

    for(ImportedBlock ib : blk.getImportedBlocksList())
    {
      BlockHeader h = ib.getHeader();
      int s = h.getShardId();
      BlockHeader parent = null;
      if (shard_friends.containsKey(s))
      {
        parent = shard_friends.get(s);
      }
      else
      {
        int p = ShardUtil.getShardParentId(s);
        parent = shard_friends.get(p);
      }
      if (parent == null)
      {
        throw new ValidationException("Unable to find parent for imported block");
      }
      ChainHash prev_hash = new ChainHash(h.getPrevBlockHash());
      if (!prev_hash.equals(parent.getSnowHash()))
      {
        throw new ValidationException("Parent hash mismatch for imported block");
      }
      if (parent.getBlockHeight() + 1 != h.getBlockHeight())
      {
        throw new ValidationException("Parent height mismatch for imported block");
      }

      for(int c : ShardUtil.getShardChildIds(s))
      {
        if (shard_friends.containsKey(c))
        {
          throw new ValidationException("Must not load shard while child shard is present");
        }
      }
      shard_friends.put(s, h);
    }

    // Check ages of our shard friends - make sure we are getting the full braid
    // The proof for this is kinda fun.  Base case is shard 0.  We need to have it and recent
    // or we need to have both children.
    // If we have both children, those will be checked.


    if (!checkBraidCompleteness(header.getBlockHeight(), params, shard_friends, 0))
    {
      {
        // map shard to block height
        TreeMap<Integer, Integer> sh_map = new TreeMap<>();
        for(Map.Entry<Integer, BlockHeader> me : shard_friends.entrySet())
        {
          sh_map.put(me.getKey(), me.getValue().getBlockHeight());
        }
        //System.out.println(String.format("Braid check fail: shard:%d h:%d friends:%s",header.getShardId(),header.getBlockHeight(), sh_map.toString()));
      }
      throw new ValidationException("Incomplete or old braid");
    }

    
    // check imported headers reference only valid hashes for shards in this chain
    // put another way, make sure all the shards are using each other and not
    // some other nonsense
    LinkedList<BlockHeader> all_headers = new LinkedList<>();
    all_headers.add(header);

    for(ImportedBlock ib : blk.getImportedBlocksList())
    {
      BlockHeader h = ib.getHeader();
      all_headers.add(h);
    }
    TreeMap<String, ChainHash> known_map=new TreeMap<>();
    checkCollisions(known_map, prev_summary.getShardHistoryMap());
    for(BlockHeader h : all_headers)
    {
      checkCollisions(known_map, h.getShardId(), h.getBlockHeight(), new ChainHash(h.getSnowHash()));
      checkCollisions(known_map, h.getShardImportMap());
    }
  }

  
  public static boolean checkBraidCompleteness(int build_height, NetworkParams params, Map<Integer, BlockHeader> shard_friends, int shard)
  {

    if (shard > params.getMaxShardId())
    {
      return false;
    }
    int good_children = 0;
    for(int c : ShardUtil.getShardChildIds(shard))
    {
      if (checkBraidCompleteness(build_height, params, shard_friends, c)) good_children++;
    }
    if (good_children == 2) return true;

    if (!shard_friends.containsKey(shard)) return false;

    int h_delta = build_height - shard_friends.get(shard).getBlockHeight();
    if (h_delta > params.getMaxShardSkewHeight())
    {
      return false;
    }
    return true;

  }

  public static void checkCollisions(TreeMap<String, ChainHash> known_map, int shard, int height, ChainHash hash)
    throws ValidationException
  {
    String key = "" + shard + "," + height;
    if (known_map.containsKey(key))
    {
      if (!hash.equals(known_map.get(key)))
      {
        throw new ValidationException("Block collision on: " + key);
      }
    }
    else
    {
      known_map.put(key, hash);
    }
  }

  /**
   * Make sure very block referenced matches from all blocks.
   * checking by shard id and height to see if hashes match
   */
  public static void checkCollisions(TreeMap<String, ChainHash> known_map, Map<Integer, BlockImportList> map)
    throws ValidationException
  {
    for(int shard : map.keySet())
    {
      for(Map.Entry<Integer, ByteString> me : map.get(shard).getHeightMap().entrySet())
      {
        int height = me.getKey();
        ChainHash hash = new ChainHash(me.getValue());
        checkCollisions(known_map, shard, height, hash);
      }
    }
  }

  /**
   * The block header need not be complete or real
   * It only needs block height and timestamp set for the purpose of this check
   * @return the fee amount in flakes if tx is good
   */
  public static long deepTransactionCheck(Transaction tx, UtxoUpdateBuffer utxo_buffer, BlockHeader block_header, 
    NetworkParams params, Set<Integer> shard_cover_set, Map<Integer, UtxoUpdateBuffer> export_buffers)
    throws ValidationException
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("Validation.deepTransactionCheck"))
    {
      TransactionInner inner = null;

      try
      {
        inner = TransactionInner.parseFrom(tx.getInnerData());
      }
      catch(java.io.IOException e)
      {
        throw new ValidationException("error parsing tx on second pass somehow", e);
      }

      long sum_of_inputs = 0L;
      // Make sure all inputs exist
      for(TransactionInput in : inner.getInputsList())
      {
        TransactionOutput matching_out = utxo_buffer.getOutputMatching(in);
        if (matching_out == null)
        {
          throw new ValidationException(String.format("No matching output for input %s", new ChainHash(in.getSrcTxId())));
        }
        validateSpendable(matching_out, block_header, params);
        sum_of_inputs += matching_out.getValue();

        // SIP-4 check
        if (block_header.getBlockHeight() >= params.getActivationHeightTxInValue())
        {
          if (in.getValue() != 0L)
          {
            if (in.getValue() != matching_out.getValue())
            {
              throw new ValidationException(String.format("Input value does not match: %d %d", matching_out.getValue(), in.getValue()));
            }
          }
        }
        utxo_buffer.useOutput(matching_out, new ChainHash(in.getSrcTxId()), in.getSrcTxOutIdx());
      }

      long spent = 0L;
      // Sum up all outputs
      int out_idx =0;
      ArrayList<ByteString> raw_output_list = TransactionUtil.extractWireFormatTxOut(tx);

      for(TransactionOutput out : inner.getOutputsList())
      {
        validateTransactionOutput(out, block_header, params);
        spent+=out.getValue();
        if (shard_cover_set.contains(out.getTargetShard()))
        {
          utxo_buffer.addOutput(raw_output_list, out, new ChainHash(tx.getTxHash()), out_idx);
        }
        else
        {
          int target_shard = out.getTargetShard();
          if (!export_buffers.containsKey(target_shard))
          {
            HashedTrie hashed_trie_mem = new HashedTrie(new TrieDBMem(), true, false);
            UtxoUpdateBuffer export_txo_buffer = new UtxoUpdateBuffer( hashed_trie_mem, UtxoUpdateBuffer.EMPTY );
            export_buffers.put(target_shard, export_txo_buffer);
          }

          export_buffers.get(target_shard).addOutput(raw_output_list, out, new ChainHash(tx.getTxHash()), out_idx);

        }
        out_idx++;

      }

      spent+=inner.getFee();

      if (!inner.getIsCoinbase())
      {
        if (sum_of_inputs != spent)
        {
          throw new ValidationException(String.format("Transaction took in %d and spent %d", sum_of_inputs, spent));
        }
      }
   
      return inner.getFee();
    }
  }

  /** This the call to validate than an old TransactionOutput is spendable now */
  public static void validateSpendable(TransactionOutput out, BlockHeader header, NetworkParams params)
    throws ValidationException
  {
    if (header.getBlockHeight() >= params.getActivationHeightTxOutRequirements())
    {
      if (out.getRequirements().getRequiredBlockHeight() > header.getBlockHeight())
      {
        throw new ValidationException(String.format("Output can not be spent until height %d", 
          out.getRequirements().getRequiredBlockHeight()));
      }
      if (out.getRequirements().getRequiredTime() > header.getTimestamp())
      {
        throw new ValidationException(String.format("Output can not be spent until time %d", 
          out.getRequirements().getRequiredTime()));
      }
    }

  }

  /** This is the call to validate that a transaction output being written now is valid */
  public static void validateTransactionOutput(TransactionOutput out, BlockHeader header, NetworkParams params)
    throws ValidationException
  {

    if (header.getBlockHeight() < params.getActivationHeightTxOutRequirements())
    {
      if (out.getRequirements().getRequiredBlockHeight() != 0)
      {
        throw new ValidationException("TxOut requirements not enabled yet");
      }
      if (out.getRequirements().getRequiredTime() != 0)
      {
        throw new ValidationException("TxOut requirements not enabled yet");
      }
    }
    else
    {
      if (out.getRequirements().getRequiredBlockHeight() < 0)
      {
        throw new ValidationException("TxOut required block height must not be negative");
      }
      if (out.getRequirements().getRequiredTime() < 0)
      {
        throw new ValidationException("TxOut required time must not be negative");
      }
    }
    if (header.getBlockHeight() < params.getActivationHeightTxOutExtras())
    {
      if (out.getForBenefitOfSpecHash().size() > 0)
      {
        throw new ValidationException("TxOut extras not enabled yet");
      }
      TransactionOutput test_out = TransactionOutput.newBuilder()
        .setRecipientSpecHash(out.getRecipientSpecHash())
        .setValue(out.getValue())
        .build();
      if (!test_out.toByteString().equals(out.toByteString()))
      {
        throw new ValidationException("TxOut extras not enabled yet, extra data found");
      }
    }
    if (out.getForBenefitOfSpecHash().size() > 0)
    {
      validateAddressSpecHash(out.getForBenefitOfSpecHash(), "TxOut for_benefit_of_spec_hash");
    }

    if (header.getVersion() == 1)
    {
      int target_shard = out.getTargetShard();
      if (target_shard != 0)
      {
        throw new ValidationException("Target shard must be zero for version 1 block");
      }
    }
    if (header.getVersion() == 2)
    {
      int target_shard = out.getTargetShard();
      validateNonNegValue(target_shard,"target_shard");
      if (target_shard > params.getMaxShardId())
      {
        throw new ValidationException("Target shard must be less than or equal max shard id");
      }

    }


  }

  public static void validateAddressSpecHash(ByteString hash, String name)
    throws ValidationException
  {
    validateByteString(hash, name, Globals.ADDRESS_SPEC_HASH_LEN);
  }

  public static void validateChainHash(ByteString hash, String name)
    throws ValidationException
  {
    validateByteString(hash, name, Globals.BLOCKCHAIN_HASH_LEN);
  }

  public static void validateByteString(ByteString hash, String name, int expected_len)
    throws ValidationException
  {
    if (hash == null)
    {
      throw new ValidationException(String.format("Missing %s", name));
    }
    if (hash.size() != expected_len)
    {
      throw new ValidationException(
        String.format("Unexpected length for %s. Expected %d, got %d", 
          name, expected_len, hash.size()));
    }
  }

  /**
   * Check the things about a transaction that can be checked without the database
   */
  public static void checkTransactionBasics(Transaction tx, boolean must_be_coinbase)
    throws ValidationException
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("Validation.checkTransactionBasics"))
    {
      if (tx.toByteString().size() > Globals.MAX_TX_SIZE)
      {
        throw new ValidationException("Transaction too big");
      }
      validateChainHash(tx.getTxHash(), "tx_hash");


      TransactionInner inner = null;

      try
      {
        CodedInputStream code_in = CodedInputStream.newInstance(tx.getInnerData().toByteArray());

        inner = TransactionInner.parseFrom(code_in);

        if (!code_in.isAtEnd())
        {
          throw new ValidationException("Extra data at end of tx inner");
        }
      }
      catch(java.io.IOException e)
      {
        throw new ValidationException(e);
      }

      MessageDigest md = DigestUtil.getMD();
      MessageDigest md_addr = DigestUtil.getMDAddressSpec();
      md.update(tx.getInnerData().toByteArray());

      ChainHash found_hash = new ChainHash(md.digest());

      if (!found_hash.equals(tx.getTxHash()))
      {
        throw new ValidationException("TX hash mismatch");
      }
      
      if (inner.getVersion() != 1)
      {
        throw new ValidationException(String.format("Unknown transaction version: %d", inner.getVersion()));
      }

      if (must_be_coinbase)
      {
        if (!inner.getIsCoinbase())
        {
          throw new ValidationException("must be coinbase");
        }
        if (inner.getInputsCount() > 0)
        {
          throw new ValidationException("coinbase must have zero inputs");
        }
        if (inner.getCoinbaseExtras().getRemarks().size() > Globals.COINBASE_REMARKS_MAX)
        {
          throw new ValidationException(String.format("Coinbase remarks of %d over max of %d", 
            inner.getCoinbaseExtras().getRemarks().size(),
            Globals.COINBASE_REMARKS_MAX));
        }
        if (tx.getSignaturesCount() != 0)
        {
          throw new ValidationException("coinbase shouldn't have signatures");
        }
        if (inner.getFee() != 0)
        {
          throw new ValidationException("coinbase shouldn't have fee");
        }
      }
      else
      {
        if (inner.getIsCoinbase())
        {
          throw new ValidationException("unexpected coinbase");
        }
        if (inner.getInputsCount() == 0)
        {
          throw new ValidationException("only coinbase can have zero inputs");
        }
        CoinbaseExtras extras = inner.getCoinbaseExtras();
        CoinbaseExtras blank = CoinbaseExtras.newBuilder().build();
        if (!extras.equals(blank))
        {
          throw new ValidationException("only coinbase can have extras");
        }
      }

      if (inner.getOutputsCount() == 0)
      {
        throw new ValidationException("Transaction with no outputs makes no sense");
      }
      if (inner.getOutputsCount() >= Globals.MAX_OUTPUTS)
      {
        throw new ValidationException("Too many outputs");
      }

      validateNonNegValue(inner.getFee(), "fee");

      HashSet<AddressSpecHash> used_address_spec_hashes = new HashSet<>();
      for(TransactionInput in : inner.getInputsList())
      {
        validateNonNegValue(in.getSrcTxOutIdx(), "input outpoint idx");
        if (in.getSrcTxOutIdx() >= Globals.MAX_OUTPUTS)
        {
          throw new ValidationException("referencing impossible output idx");
        }
        validateAddressSpecHash(in.getSpecHash(), "input spec hash");
        validateChainHash(in.getSrcTxId(), "input transaction id");

        used_address_spec_hashes.add(new AddressSpecHash(in.getSpecHash()));
      }
      if (used_address_spec_hashes.size() != inner.getClaimsCount())
      {
        throw new ValidationException(String.format("Mismatch of used spec hashes (%d) and claims (%d)", 
          used_address_spec_hashes.size(),
          inner.getClaimsCount()));
      }

      HashSet<AddressSpecHash> remaining_specs = new HashSet<>();
      remaining_specs.addAll(used_address_spec_hashes);

      for(AddressSpec spec : inner.getClaimsList())
      {
        AddressSpecHash spechash = AddressUtil.getHashForSpec(spec, md_addr);
        if (!remaining_specs.contains(spechash))
        {
          throw new ValidationException(String.format("claim for unused spec hash %s", spechash.toString()));
        }
        remaining_specs.remove(spechash);

      }
      Assert.assertEquals(0, remaining_specs.size());

      //Now we know the address spec list we have covers all inputs.  Now we have to make sure the signatures match up.

      // Maps claim idx -> set(key idx) of signed public keys
      // such that signed_claim_map.get(i).size() can be used to see if there are enough
      // signed keys for claim 'i'.
      TreeMap<Integer, Set<Integer> > signed_claim_map = new TreeMap<>();

      for(SignatureEntry se : tx.getSignaturesList())
      {
        if (inner.getClaimsCount() <= se.getClaimIdx()) throw new ValidationException("Signature entry for non-existant claim");
        AddressSpec spec = inner.getClaims(se.getClaimIdx());

        if (spec.getSigSpecsCount() <= se.getKeyIdx()) throw new ValidationException("Signature entry for non-existant sig spec");
        SigSpec sig_spec = spec.getSigSpecs(se.getKeyIdx());

        if (!SignatureUtil.checkSignature( sig_spec, tx.getTxHash(), se.getSignature()))
        {
          throw new ValidationException("signature failed");
        }
        //So we have a valid signature on a valid claim!  woot

        if (!signed_claim_map.containsKey(se.getClaimIdx())) signed_claim_map.put(se.getClaimIdx(), new TreeSet<Integer>());

        Set<Integer> set = signed_claim_map.get(se.getClaimIdx());
        if (set.contains(se.getKeyIdx())) throw new ValidationException("duplicate signatures for claim");

        set.add(se.getKeyIdx());
      }

      // Make sure each claim is satisfied
      for(int claim_idx = 0; claim_idx < inner.getClaimsCount(); claim_idx++)
      {
        int found =0;
        if (signed_claim_map.containsKey(claim_idx)) found = signed_claim_map.get(claim_idx).size();
        AddressSpec claim = inner.getClaims(claim_idx);

        if (found < claim.getRequiredSigners())
        {
          throw new ValidationException(
            String.format("Claim %d only has %d of %d needed signatures", 
              claim_idx, found, claim.getRequiredSigners()));
        }
      }

      //Sanity check outputs
      for(TransactionOutput out : inner.getOutputsList())
      {
         validatePositiveValue(out.getValue(), "output value");
         validateAddressSpecHash(out.getRecipientSpecHash(), "output spec hash");
      }

      if (inner.getExtra().size() > Globals.MAX_TX_EXTRA)
      {
        throw new ValidationException("Extra string too long");
      }
    }

  }

  /**
   * Validates that 
   *  - the transaction output lists match the 
   *    shard_export_root_hash from the source block
   *  - all the output lists that should be imported (and only those)
   *    have been
   */
  public static void validateShardImport(NetworkParams params, ImportedBlock import_blk, int my_shard_id, UtxoUpdateBuffer utxo_buff)
    throws ValidationException
  {

    checkBlockHeaderBasics(params, import_blk.getHeader(), false);

    Set<Integer> cover_set = ShardUtil.getCoverSet(my_shard_id, params);
    TreeSet<Integer> expected_set = new TreeSet<>();
 
    // Make sure we are importing all the shards we should from source header
    for(int s : cover_set)
    {
      if (import_blk.getHeader().getShardExportRootHashMap().containsKey(s))
      {
        expected_set.add(s);
        if (!import_blk.getImportOutputsMap().containsKey(s))
        {
          throw new ValidationException("Expected block to import outputs for shard " + s);
        }
      }
    }

    // Make sure the shards we import are in our expected set
    for(int s : import_blk.getImportOutputsMap().keySet())
    {
      if (!expected_set.contains(s))
      {
        throw new ValidationException("Unexpected shard in import: " + s);
      }
      ChainHash found_utxo = getUtxoHashOfImportedOutputList( import_blk.getImportOutputsMap().get(s), s);
      ChainHash header_utxo = new ChainHash(import_blk.getHeader().getShardExportRootHashMap().get(s));

      if (!header_utxo.equals(found_utxo))
      {
        throw new ValidationException("Import list utxo mismatch");
      }
      utxo_buff.addOutputs(import_blk.getImportOutputsMap().get(s) );
    }

  }

  public static ChainHash getUtxoHashOfImportedOutputList(ImportedOutputList lst, int expected_shard)
    throws ValidationException
  {
    HashedTrie hashed_trie = new HashedTrie(new TrieDBMem(), true, false);

    UtxoUpdateBuffer utxo_buffer = new UtxoUpdateBuffer( hashed_trie, UtxoUpdateBuffer.EMPTY );

    for(ImportedOutput io : lst.getTxOutsList())
    {
      try
      {
        TransactionOutput out = TransactionOutput.parseFrom(io.getRawOutput());
        if (out.getTargetShard()!=expected_shard)
        {
          throw new ValidationException("Import for unexpected shard");
        }
      }
      catch(com.google.protobuf.InvalidProtocolBufferException e)
      {
        throw new ValidationException(e);
      }

      validateChainHash( io.getTxId(), "import tx_id");
      validateNonNegValue( io.getOutIdx(), "import out_idx");

    }

    utxo_buffer.addOutputs(lst);

    return utxo_buffer.simulateUpdates();


  }

  public static void validateNonNegValue(long val, String field)
    throws ValidationException
  {
    if (val < 0) throw new ValidationException(String.format("%s must be non-negative - %d", field, val));
  }
  public static void validatePositiveValue(long val, String field)
    throws ValidationException
  {
    if (val <= 0) throw new ValidationException(String.format("%s must be positive - %d", field, val));
  }
}
