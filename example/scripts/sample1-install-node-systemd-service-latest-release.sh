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
echo "Installing $release_name node in $snowblossom_home"

# install java
apt-get update
apt-get -yq install openjdk-8-jdk unzip

# create a dedicated user, if doesn't exist
id -u snowblossom &>/dev/null || useradd --home-dir "$snowblossom_home" --create-home --system snowblossom

# download latest release
cd "$snowblossom_home"
wget --continue "$release"
unzip "$release_file"
cp --recursive --no-clobber "$release_name"/* ./
cp "$release_name"/*.jar ./

# install systemd service and
# automatically start at boot
systemctl enable "$snowblossom_home/systemd/snowblossom-node.service"
# (re)start
systemctl restart snowblossom-node.service

echo "Done!"
echo "You can manage the systemd service normally with:"
echo "manage:   systemctl {start|stop|restart} snowblossom-node.service"
echo "logs  :   journalctl -n 100 -f -u snowblossom-node.service"
