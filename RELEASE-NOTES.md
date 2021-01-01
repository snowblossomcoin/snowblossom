## master

* Update to bouncy-castle 1.68
* Added database type "atomic_file" for use with MrPlow
* Added data migration tool for MrPlow data

## 1.8.0

* Update seed nodes
* Add remote address to Shackleton explorer logs
* Switch Shackleton web handling over to new web framework
* Added explorer APIs zone and /api/total_coins
* Lower max history replies
* Add get_address_history rpc
* SIP4 - Input value checking set for block 151680
* Fixed presentation bug in unspent with mempool
* Added public note string to iceleaf send panel

## 1.7.1

* Added History panel to iceleaf GUI
* Added SubscribeAddressUpdates gRPC api to node to monitor addresses
* Added MonitorTool to use SubscribeAddressUpdates to monitor addresses

## 1.7.0

* Allow reserving channel name in CLI
* Adding username and channel identifiers to explorer
* Switching MrPlow to continue using the same channel
* Switching from maven-jar to new maven-install based system for bazel 2.0
* Added watch-only note on wallet summary page of iceleaf GUI
* Add details button to iceleaf GUI to get xpubs and seeds of wallets
* Fixed bug in re-index for tx history - will automatically repair db on restart
* Switching to much newer version of gRPC
* Added firm warning message about 32-bit JVM
* Break and fix mining share calculation
  * Broken in commit a577f01ada35ca19e5fe2dd971a8880d5fc6c0bb (2019.11.22)
  * Fixed in commit 03254fbf759ab0947d377458384efb83e6ee6e09 (2020.02.03)
* Added streaming block template so MrPlow can update block template without breaking the connection
  * MrPlow will need the SnowBlossomNode to be updated to run
* Added RichList report to explorer
* Update bouncy castle to 1.65

## 1.6.0

* Changing tx_index and addr_index over to Hashed Trie mode
* Adding FBO and name values to hashed trie as well
* Those above two will involve an index rebuild to update the database
  * This should take a few minutes
* Added RPC call for get_fbo_list and get_id_list
* Added RPC option to get_transaction to return json with "send_json" option

## 1.5.2

* Skip merging PeerInfo entries, really not needed
* Adding support for host_uri in configs.  See https://wiki.snowblossom.org/index.php/ConfigOption/node_uri
* Add client ability to autoselect fastest node
* IceLeaf GUI client
* Xpub support for watch only wallets

## 1.5.1

* Adding SurfMiner using new wave mining method
* Updating Arktika, PoolMiner and SurfMiner to use fewer memory allocations
* Allows Arktika to read a single blob snowfield just like PoolMiner and SurfMiner
* Change rocksdb log retention to 5 files
* In client jsonrpc get_unspent add optional parameter to specify address to query
* In client jsonrpc add get_block by height or hash
* Switch to SecureRandom for seed generation since Random only uses a 64-bit seed
* Adding audit log mode: https://wiki.snowblossom.org/index.php/Audit_Log_Mode
* Calculator on explorer
* Add xpub support for seed based wallets.  TODO: watch-only mode for xpubs
* RPC get status returns balance matching other balance results
* Add get_address_balance RPC
* Add optional TLS service port
* Fixed log levels so that they are respected for things outside of snowblossom

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

