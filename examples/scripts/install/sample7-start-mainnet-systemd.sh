#!/bin/bash
if [[ $EUID -ne 0 ]]; then
   echo "This script requires root!"
   exit 1
fi


snowblossom_home=/var/snowblossom


echo
while true; do
	echo -ne "\r"`date`" Checking for block."
	trigger_blockhash=`wget -qO - https://blockexplorer.com/api/block-index/523000 | grep -Po 'blockHash":"\K([^"]*)'`
	if [ -z trigger_blockhash ]; then
		echo $trigger_blockhash
		echo "IT'S TIME!"
		break
	fi
	sleep 5s
done


# switch to user
su - snowblossom <<EOF

cd "$snowblossom_home/source/snowblossom/
# set start hash

# build snowblossom
bazel build :all

EOF

# Start!
systemctl start snowblossom-node-mainnet.service
systemctl start snowblossom-miner-mainnet.service
journalctl -f -u snowblossom-miner-mainnet.service
