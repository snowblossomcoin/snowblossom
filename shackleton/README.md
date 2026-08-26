# Shackleton – the Snowblossom block explorer

Shackleton is a self-contained web explorer for a Snowblossom network.  It talks to a
regular Snowblossom node over gRPC and serves HTML pages plus a JSON API.  It fully
supports sharded networks: every page is shard aware (block lists, block imports and
exports, per shard balances, the shard tree, per shard statistics and the rich list
over all shards).

## Running

```
bazel build :ShackletonExplorer
bazel-bin/ShackletonExplorer explorer.conf
```

The node should run with `addr_index=true` and `tx_index=true`, otherwise address
history and transaction lookups are not available.  For a sharded network the node
should track all shards (the default when no `shards` list is configured).

## Configuration (`explorer.conf`)

| key | default | description |
| --- | --- | --- |
| `port` | required | HTTP port to listen on |
| `node_host` / `node_port` | | node to connect to (plain gRPC) |
| `node_uri` | | alternative: `grpc://host:port` or `grpc+tls://host:port?key=node:...` |
| `network` | `snowblossom` | `snowblossom` (mainnet), `teapot` (testnet), `spoon` (regtest), ... |
| `index_depth` | 300 | recent blocks kept in memory per shard (also the 24h statistics window source) |
| `mining_look_back` | 1000 | blocks per shard analysed for pool / miner / vote statistics |
| `history_days` | 365 | days of block history kept in memory for the statistics page (ranges 24h … 1 year) |
| `richlist_interval_min` | 45 | minutes between rich list (full UTXO walk) updates |
| `web_threads` | 32 | web server threads |
| `log_config_file` | | java.util.logging properties file |

## Pages

* `/` – network overview, shard table, latest blocks of all shards (auto refreshing)
* `/blocks`, `/blocks?shard=S&from=H` – block lists, per shard with infinite scrolling
* `/block/{hash}`, `/block/{shard}/{height}`, `/block/{height}` – block details: header, reward
  breakdown (subsidy + fees), shard imports / exports, imported blocks, transactions with resolved
  input values, navigation to previous / next blocks (following shard splits)
* `/tx/{hash}` – transaction details, confirmation status (located in the right shard), claims and signatures
* `/address/{address}` – balances (confirmed / pending / spendable, per shard), unconfirmed transactions,
  identifiers, paginated transaction history with net amounts
* `/shards`, `/shard/{id}` – shard tree, split points, per shard statistics and split readiness
* `/richlist` – addresses by balance over all shards, distribution statistics
* `/mining` – pools (coinbase remarks), miner addresses, motion votes, mining calculator
* `/stats` – snow fields, daily charts (hashrate, difficulty, blocks, transactions)
* `/peers` – known peers and their versions (requires a node whose `GetPeerList` RPC completes)
* `/search?q=...` – block hash, tx hash, address, height, `shard:height`, username or channel name
* `/api` – API documentation

Old links of the form `/?search=...` are redirected.

## JSON API

See `/api` on a running explorer.  Notable endpoints: `/api/status`, `/api/shards`,
`/api/block/{hash}`, `/api/block/{shard}/{height}`, `/api/blocks`, `/api/tx/{hash}`,
`/api/address/{address}?history=1`, `/api/richlist`, `/api/mining`, `/api/history`,
plus the legacy `/api/total_coins`, `/api/total_coins_json`, `/api/health_stats`,
`/api/recent_json_graph` and `/api/block_info_by_height/{shard}/{height}`.

## Architecture

* `ChainCache` – caches headers, blocks, summaries and transactions by hash (immutable data).
* `ChainIndex` – background thread keeping the recent blocks of every active shard, per shard and
  network statistics and the shard tree (which shards exist, where they split).
* `MiningStats`, `HistoryStats`, `RichList` – background statistics.
* `TxLocator` – finds the block and shard a transaction was confirmed in (the node's own
  transaction status only looks at shard 0).
* `AddressBalance` – balances are derived from the rich list UTXO snapshot (exact at a known block)
  plus the blocks mined since then (held in memory by `ChainIndex`) and the mempool; the per-address
  UTXO trie walk, which takes a minute for addresses with tens of thousands of outputs, is only a fallback.
  Page 1 of an address' transaction list is likewise built from the recent blocks in memory; older pages
  use the node's address history index.
* `WebServer` – routing; `*Page` classes render HTML; `ApiHandler` renders JSON; `Html` holds the
  page chrome, CSS and formatting helpers.  No external CSS/JS dependencies.
