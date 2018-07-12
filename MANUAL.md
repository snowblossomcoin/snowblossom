
# Building Binaries

* Install bazel - https://docs.bazel.build/versions/master/install.html
* Clone git repo and build:
```bash
  git clone https://github.com/snowblossomcoin/snowblossom.git
  cd snowblossom
  bazel build :all
```

To build jar files that can be copied around (including to windows) do:


# Types of Binaries

## SnowBlossomNode

This is the Snowblossom P2P full node.  It connects to other nodes, downloads and verifies the blockchain.  It acts as a server for miners and clients.

## SnowBlossomClient

This is the Snowblossom basic wallet.  It needs a SnowBlossomNode to connect to, it doesn't need to be your node but for privacy it is recommended to run your own node.

## SnowBlossomMiner

This is the mining agent, it conencts to a node (doesn't have to be your node) to get block templates and then submits them back when it solves them.  Requires a local copy of snow fields to work or can create them as needed.

## PoolMiner / Arktika

These are different mining agents.  They connect to a mining pool to get work.  Requires a local copy of the snow fields to work.

## MrPlow

A simple PPLNS mining pool.  Needs a node to connect to.

## ShackletonExplorer

A web server that shows stats.  Needs a node to connect to.

## SnowFall

Program that generates snow fields.  It is recommended to use tools/snowfall... scripts rather than running directly
or simply turn on auto_snow=true in the miner.

## SnowMerkle

Program that generates the snow field decks and verifies the overall hash of the field.  The decks are required in conjunction with the snow fields themselves to mine.  The decks are files of intermediate hash values used to greatly speed up block pow proof generation.
It is recommended to use tools/snowfall... scripts rather than running directly or simple turn on auto_snow=true in the miner.


