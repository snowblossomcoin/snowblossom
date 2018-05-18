#!/bin/bash
if [[ $EUID -ne 0 ]]; then
   echo "This script requires root!"
   exit 1
fi

snowblossom_home="`eval echo "~$SUDO_USER"`/.snowblossom"

echo "Installing snowblossom master in $snowblossom_home"

# install openjdk-8-jdk and bazel
echo "deb [arch=amd64] http://storage.googleapis.com/bazel-apt stable jdk1.8" > /etc/apt/sources.list.d/snowblossom-bazel.list
wget -qO - https://bazel.build/bazel-release.pub.gpg | apt-key add -
apt-get update
apt-get -yq install git openjdk-8-jdk bazel

# as user
su - $SUDO_USER <<EOF

# download source
mkdir -p "$snowblossom_home/source" && cd "$snowblossom_home/source"
git clone https://github.com/snowblossomcoin/snowblossom.git

# build
cd snowblossom
bazel build :all

# copy sample config files
cp --no-clobber --recursive "$snowblossom_home/source/snowblossom/examples/configs" "$snowblossom_home/"
chmod 750 -R "$snowblossom_home/configs"

cd "$snowblossom_home"
$snowblossom_home/source/snowblossom/bazel-bin/SnowBlossomNode configs/node-testnet.conf
EOF

