
# Building Binaries

* Install bazel - https://docs.bazel.build/versions/master/install.html
* Clone git repo and build:
```bash
  git clone https://github.com/snowblossomcoin/snowblossom.git
  cd snowblossom
  bazel build :all
```

# Types of Binaries

## SnowBlossomNode

This is the Snowblossom P2P full node.  It connects to other nodes, downloads and verifies the blockchain.  It acts as a server for miners and clients.

## SnowBlossomClient

This is the Snowblossom basic wallet.  It needs a SnowBlossomNode to connect to, it doesn't need to be your node but for privacy it is recommended to run your own node.

## SnowBlossomMiner

## SnowFall

## SnowMerkle


