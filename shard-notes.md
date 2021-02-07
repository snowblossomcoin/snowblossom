
# Sharding

## Notes

### UTXO block proof

Suppose there is some node A that is a full validator for shard S, meaning it has ingested
and checked all blocks.

Suppose there is some node B that is not a full validator of shard S.  It is only looking
at and storing headers.

Supposed B accepts that block N on shard S is valid, due to network concensus.

A can make a proof that proves that block N+1 is valid to B.

This can be done by A providing the block N+1.  In addition A would provide
enough of the UTXO internal nodes to prove that all Transactions Outputs spend by that block
were in the block N UTXO hash.  A would also provide other internal nodes to prove
that the UTXO changes in block N+1 mutate to be the  UTXO hash in block N+1.

This would be a significant chunk of the UTXO tree, but not nearly all of it.

B, using this data to validate the block could then discard rather than store the validation
data.

This would be some intense code, but it is very doable.




