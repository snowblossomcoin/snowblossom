package snowblossom.lib;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import org.junit.Assert;
import snowblossom.proto.*;
import snowblossom.lib.trie.HashedTrie;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.*;

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

      if (header.getVersion() != 1)
      {
        throw new ValidationException(String.format("Unknown block version: %d", header.getVersion()));
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

      // TODO - make sure target is correct 

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
      if (blk.getHeader().getBlockHeight() == 0)
      {
        if (!coinbase_inner.getCoinbaseExtras().getRemarks().startsWith( params.getBlockZeroRemark()))
        {
          throw new ValidationException("Block zero remark must start with defined remark");
        }
      }

      UtxoUpdateBuffer utxo_buffer = new UtxoUpdateBuffer(utxo_hashed_trie, 
        new ChainHash(prev_summary.getHeader().getUtxoRootHash()));
      long fee_sum = 0L;

      for(Transaction tx : blk.getTransactionsList())
      {
        fee_sum += deepTransactionCheck(tx, utxo_buffer, blk.getHeader(), params);
      }

      long reward = PowUtil.getBlockReward(params, blk.getHeader().getBlockHeight());
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

  /**
   * The block header need not be complete or real
   * It only needs block height and timestamp set for the purpose of this check
   * @return the fee amount in flakes if tx is good
   */
  public static long deepTransactionCheck(Transaction tx, UtxoUpdateBuffer utxo_buffer, BlockHeader block_header, NetworkParams params)
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
        utxo_buffer.addOutput(raw_output_list, out, new ChainHash(tx.getTxHash()), out_idx);
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
