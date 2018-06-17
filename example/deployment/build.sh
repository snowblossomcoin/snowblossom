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
cp ../../bazel-bin/SnowBlossomNode_deploy.jar "$name"/
cp ../../bazel-bin/SnowBlossomClient_deploy.jar "$name"/
cp ../../bazel-bin/SnowBlossomMiner_deploy.jar "$name"/
cp ../../bazel-bin/PoolMiner_deploy.jar "$name"/
cp -R configs "$name"/
mkdir "$name/logs"

# convert line endings to make easily windows editable -_-;
for i in "$name/configs"/*; do sed -i 's/\n$/\n\r$/' "$i"; done
for i in "$name"/*.bat; do sed -i 's/\n$/\n\r$/' "$i"; done

zip -r -9 "$name.zip" "$name"
#tar -czf "$name.tar.gz" "$name"
rm -r "$name"
