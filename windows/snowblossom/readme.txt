## Download Self-contained Zip file

[snow-win-mainnet-launch.zip](https://snowblossom.org/releases/snow-win-mainnet-launch.zip)

Extract that file.

## Download snowfield torrent files - only needed for mining

You can let the miner binary build these automatically, but it does take some time so you may prefer to download them.
https://snowblossom.org/snowfields/index.html

To start with, you'll probably want snowblossom.0 and snowblossom.1.  More will be required as the network increases hashing power.

Put the resulting files in the "snow" directory created by extracting the zip file above.

## Start Node

* Run node.bat  
* That will start up a terminal window, leave that running.

## Start Client

* Run client.bat
* That will generate some addresses and display your balance.  You can leave that running or close it.

## Start Miner

* Run miner.bat
* This will use your local node for information to mine.  When it is working properly it will display a hash rate
that looks like:
  ** INFO: Mining rate: 46110.5/sec
* When it finds a block, the client display should soon update with the block value.

