#!/bin/bash
if [[ $EUID -ne 0 ]]; then
   echo "This script requires root!"
   exit 1
fi

# exit upon any error
set -eu

snowblossom_home=/var/snowblossom
echo "Building snowblossom from source in $snowblossom_home"

# install openjdk-8-jdk and bazel
echo "deb [arch=amd64] http://storage.googleapis.com/bazel-apt stable jdk1.8" > /etc/apt/sources.list.d/snowblossom-bazel.list
wget -qO - https://bazel.build/bazel-release.pub.gpg | apt-key add -
apt-get update
apt-get -yq install git openjdk-8-jdk bazel git

# create a dedicated user, if doesn't exist
id -u snowblossom &>/dev/null || useradd --home-dir "$snowblossom_home" --create-home --system snowblossom

# build as user
su - snowblossom <<EOF

# download
cd "$snowblossom_home"
rm -rf source
git clone "https://github.com/snowblossomcoin/snowblossom.git" source

# build
cd source
bazel build :all

# setup simple helpful run scrips?
cd "$snowblossom_home"
cp -r source/example/configs ./
echo '#!/bin/bash\nsource/bazel-bin/SnowBlossomNode configs/node.conf' > node.sh
echo '#!/bin/bash\nsource/bazel-bin/SnowBlossomClient configs/client.conf \$1 \$2 \$3' > client.sh
echo '#!/bin/bash\nsource/bazel-bin/SnowBlossomMiner configs/miner.conf' > miner.sh
chmod +x *.sh
mkdir -p "logs"
EOF

cat <<EOF
Done.
You can manually run with:  node.sh,  client.sh,  miner.sh
or you can setup systemd services with:
    systemctl link /var/snowblossom/source/example/systemd/snowblossom-node.service
    systemctl link /var/snowblossom/source/example/systemd/snowblossom-miner.service
    systemctl link /var/snowblossom/source/example/systemd/snowblossom-pool.service
EOF
