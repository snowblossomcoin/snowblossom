package snowblossom;

import snowblossom.proto.Block;
import snowblossom.proto.BlockHeader;
import snowblossom.proto.Transaction;
import snowblossom.proto.SnowPowProof;
import snowblossom.proto.TransactionInner;
import snowblossom.proto.CoinbaseExtras;
import snowblossom.proto.TransactionInput;
import snowblossom.proto.TransactionOutput;
import snowblossom.proto.AddressSpec;
import snowblossom.proto.SigSpec;
import snowblossom.proto.SignatureEntry;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;

import java.security.MessageDigest;

import java.util.LinkedList;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Set;
import java.util.ArrayList;

import org.junit.Assert;

public class Validation
{
  /**
   * Check the things about a block that can be checked without the database
   */
  public static void checkBlockBasics(NetworkParams params, Block blk, boolean require_transactions)
    throws ValidationException
  {
    BlockHeader header = blk.getHeader();
    if (header == null) throw new ValidationException("Header missing");

    if (header.getVersion() != 1)
    {
      throw new ValidationException(String.format("Unknown block version: %d", header.getVersion()));
    }

    validateChainHash(header.getPrevBlockHash(), "prev_block_hash");
    validateChainHash(header.getMerkleRootHash(), "merkle_root_hash");
    validateChainHash(header.getUtxoRootHash(), "utxo_root_hash");
    validateChainHash(header.getSnowHash(), "snow_hash");

    validateByteString(header.getNonce(), "nonce", Globals.NONCE_LENGTH);
    validateByteString(header.getTarget(), "target", 8);

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
      if (!SnowMerkleProof.checkProof(proof, field_info.getMerkleRootHash(), field_info.getLength()))
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

    if (!PowUtil.lessThanTarget(context, header.getTarget()))
    {
      throw new ValidationException("Hash not less than target");
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

    validateNonNegValue(inner.getFee(), "fee");

    HashSet<AddressSpecHash> used_address_spec_hashes = new HashSet<>();
    for(TransactionInput in : inner.getInputsList())
    {
      validateNonNegValue(in.getSrcTxOutIdx(), "input outpoint idx");
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
