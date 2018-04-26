# Snowblossom

Snowblossom is a simple cryptocurrency with a few novel ideas.

## Naming

The name was selected based on a random name generator because I needed something to call this while in development and it kinda stuck.

## Dynamic difficulty recalculation

Snowblossom calculates a new difficulty not only with each block, but as time goes on
from the last block the difficulty adjusts.  This means it should quickly and smoothly
adjust for changes in mining power.

## IO based POW

Snowblossom uses a Proof-of-Work (PoW) function that uses a large deterministically generated
file as part of the calculation.  This means that mining will be IO limited.

The objective is to have a PoW such that a modern gaming machine with commodity hardware will always be competitive with exotic custom hardware.  Gamers love fast IO so any major advances in large and fast disks or memory should quickly make it into the PC gaming space.  Sure, someone could build a large array of super fast memory but it is unlikely to be more than a small multiplier faster than current PC memory technology.

Right now with Bitcoin, about a $1000 investment can get you an ASIC that runs at about 10 TH (terahashes/s) while a modern gaming GPU of similar price can probably get about 200 MH/s.  That is a 50,000x speed improvement, which means that Bitcoin mining is completely dominated by those willing to buy extremely custom hardware for the purpose.  This isn't necessarily bad, but I think an ecosystem with a larger number of small miners is likely to be healthier than one with a small number of large miners.

### Snow field generation

To make the IO based PoW function work, we need large files that must be referenced by miners.  The files need to be agreed upon and well known.  They also have to come from shared and available algorithm, otherwise there would always be a suspicion that the person creating
the files has some shortcut to their creation which would allow for sections of the files to be built as needed in memory and break the need to do large random IO.  So the files need to be created from a deterministic algorithm that anyone can run and see how it works.

In SnowFall, the algorithm does the following:
* An initial pass writing the file from an PRNG (Pseudo random number generator)
* Divides the file into 4k chunks (matching modern drive page size).  Does 7 * number of chunks read, modify, write operations rewriting arbitrary pages using the PRNG to select pages.  Each time using the previous contents of the page to modify the PRNG state
* Through the entire process, uses a FIFO entropy buffer to effectively make the state size of the PRNG very large (256mb).  This makes
it very difficult for someone to save the state of the PRNG to be able to build pages on demand.

So thus we get a rather long process that makes a snow field of a specified size.  Anyone can run SnowFall to make the files and compare them to be published versions.  However, those not wishing to run the program (which is a bit slow) can also download them via bittorrent.

### Snow field use

So the PoW needs the following properties to be effective:
* Slow to run, needing IO and big file storage
* Fast to verify without IO or those files

So the PoW is basically this:
* Hash the normal header information (last block, tx merkle root, etc).  Use this hash to pick an offset into the snow field file.
* Use the contents of that location in the snow field file to make a new hash, combining the previous hash as well.  Use that to select
a next location in the snow field file. (repeat 6x)

Take the final hash at the end and compare it to the difficulty target to see if it is a match.

However, nodes without the snow field files or lite clients need to be able to verify the PoW.  So we hard code the merkle roots
of the snow field files.  When mining the miner has to make a merkle proof to prove that the referenced data for the PoW is in the
required location.  This way, we add a small amount of data to the block header (about 2k assuming a 1TB snow field) that allows
any node or client that has the merkle root of that file to quickly verify the block without the need to actually having the snow field files.

### Snow storms

The world is full of surprises so snowblossom starts with a smallish snow field (1gb) and when difficulty running averages are hit, will switch to the next larger snow field.  Then any subsequent blocks will have to do the PoW using the larger snow field.  This switch-over will
be called a snow storm.  They should happen infrequently and with enough notice for people to download or generate the new files.

## UTXO Merkle in block header

A somewhat minor but significant improvement is that the snowblossom block header will contain a merkle root of the current utxo state
that is in effect with that block.  This way, nodes serving lite clients can give complete merkle proofs for unspent outputs for addresses.
The proofs can also be used to prove non-existence or completeness of utxos for an address.  This way, a client can be sure the
server is not withholding information from them.

## Simple and Fresh Implementation

Snowblossom is written completely from scratch using modern tools and protocols to keep the code size small and as simple as possible.

It does not support contracts, op codes.  It only supports simple address and multisig outputs.



