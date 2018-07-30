#!/bin/bash
if [[ $EUID -ne 0 ]]; then
	echo "This script requires root!"
	exit 1
fi

# exit upon any error
set -eu

# release info
snowblossom_home=/var/snowblossom
release_info=`wget -qO - https://api.github.com/repos/snowblossomcoin/snowblossom/releases`
release=`echo "$release_info" | grep -m1 -Po 'browser_download_url": "\K[^"]+'`
release_file="${release##*/}"
release_name="${release_file%.*}"
echo "Installing $release_name pool-miner in $snowblossom_home"

# install java
apt-get update
apt-get -yq install git openjdk-8-jdk unzip

# create a dedicated user, if doesn't exist
id -u snowblossom &>/dev/null || useradd --home-dir "$snowblossom_home" --create-home --system snowblossom

# download latest release
cd "$snowblossom_home"
wget -N "$release"
unzip "$release_file" -d ./
mv -n "$release_name"/* ./

# install systemd service and
# automatically start at boot
systemctl enable "$snowblossom_home/systemd/snowblossom-pool-miner.service" 
# (re)start
# systemctl restart snowblossom-pool-miner.service

echo "Done! NOT Running."
echo "You can manage the systemd service normally with:"
echo "manage:   systemctl {start|stop|restart} snowblossom-pool-miner.service"
echo "logs  :   journalctl -n 100 -f -u snowblossom-pool-miner.service"
