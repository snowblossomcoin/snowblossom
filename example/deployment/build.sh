#!/bin/sh

# 1. create new release git tag --annotate 1.0.0 #(or create on github and git pull)
# 2. run this to create standalone deployment
# 3. Remember to push tags/release to github and upload the deployment

# exit if anything fails
set -eu

# build snowblossom
cd ../../
bazel build \
	:SnowBlossomNode_deploy.jar \
	:SnowBlossomClient_deploy.jar \
	:SnowBlossomMiner_deploy.jar \
	:PoolMiner_deploy.jar

version=`git describe`
name="snowblossom-$version"

cd -
cp -r "snowblossom" "$name"

mkdir -p "$name/configs"

#node
cp ../../bazel-bin/SnowBlossomNode_deploy.jar "$name"/
cp "configs/node.conf" "$name/configs/"

#client
cp ../../bazel-bin/SnowBlossomClient_deploy.jar "$name"/
cp "configs/client.conf" "$name/configs/"

#miner
#cp ../../bazel-bin/SnowBlossomMiner_deploy.jar "$name"/
#cp "configs/miner.conf" "$name/configs/"

#pool-miner
cp ../../bazel-bin/PoolMiner_deploy.jar "$name"/
cp "configs/pool-miner.conf" "$name/configs/"

# make logs
mkdir -p "$name/logs/"
cp "configs/logging.properties" "$name/configs/"
touch "$name/logs/logs.placeholder"

# convert line endings to make easily windows editable -_-;
for i in "$name/configs"/*; do sed -i 's/(?<=\r)$/\r$/' "$i"; done
for i in "$name"/*.bat; do sed -i 's/(?<=\r)$/\r$/' "$i"; done

zip -r -9 "$name.zip" "$name"
#tar -czf "$name.tar.gz" "$name"
rm -rf "$name"
