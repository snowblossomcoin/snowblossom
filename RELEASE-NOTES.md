## 1.5.0

* Adding benchmarking features to Arktika
* Improve error logging in Arktika
* Add terrible vanity address generator
* Add terrible hole generator that is of no use
* Added exceptions for invalid characters in address, previously would have probably
  been a checksum failure
* Updated 'monitor' command to only print a new line when something is different
* Added ability to send 'all' to empty a wallet
* Update proto rules to new system, requires newer bazel
* Add BIP44 based seed wallets
* Switch default wallet type to BIP44
* Added 'import_seed' and 'show_seed' commands
* Added dynamic fee estimation based on mempool
* Notes for wallet type migration: https://wiki.snowblossom.org/index.php/Migrate:_Standard_Keys_to_Seed_Keys


## 1.4.2

* Only allow 100k of low fee transactions per block when building a block
* When mempool is half full, reject new low fee transactions
* MrPool lowers difficulty if share rate is low
* Switch miners to faster MultiAtomicLong for hash counting
* Add stats outputs to arktika
* Abstract pool client interaction into PoolClient class
* Enable mining client failover among a list of pools

## 1.4.1

* fix for tx cluster gossip

## 1.4.0

* allow signing arbitrary data
* implemented SIP3

## 1.3.3

* explorer shows tx status
* watch only wallet
* rpc import wallet
* rpc features, estimate transaction size, transaction status, get_status, get_unspent, etc

## 1.3.0

* rpc calls
* wallet stats
* updated &quot;bouncy castle&quot; library to 1.6.0
* wallet maintains a pool of new keys
* minor improvements and tuning

## 1.2.0

* new experimental distributed miner &quot;arktika&quot;
* explorer improvements: address history, search by block index

## 1.1.2

* implemented SIP1

## 1.0.8

* node change default database location, NOTE may cause resync for users
* global deployment refactor (identical deployment for windows AND linux)
* proposal passed - [[SIP1 Fix Block Reward Halving Time|SIP1-Fix-Block-Reward-Halving-Time]]

## 1.0.7

* pool report individual miner rates
* &quot;hybrid mining&quot; (part ram, part storage media)
* pool dynamic difficulty

## 1.0.6

* alpha mining pool
* general improvements

## 1.0.5

* node peerage improvements
* code reorganization
* general improvements

## 1.0.4

* explorer reports node version counts
* logging improvements
* code improvements
* explorer improvements

## &lt; 1.0.2

* memfield (ram based mining)
* miner reports mining metrics
* node database option lobstack
* submit node id for network counting
* node tx_index
* client see pending transactions
* windows build
* block explorer
* load testing
* autosnow

