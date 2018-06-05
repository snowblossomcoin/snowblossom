# Snowblossom Startup Guide

On windows you can just run the .bat files.  Other platforms should work as well, just pretend
the .bat files are shell scripts.

If you see errors about "snowblossom.db.rocksdb.JRocksDB" then in your node.conf set:

  db_type=lobstack

Issue is discussed here: https://github.com/snowblossomcoin/snowblossom/issues/32


Start order:

1) node.bat first to start a node, let that sync
2) client.bat to create a wallet - you can leave this running to watch or close it
3) miner.bat to mine (or miner-mem.bat) if you can fit the current field in memory. 
You may need to edit miner-mem.bat to include -Xmx of the current field size plus 2gb.


