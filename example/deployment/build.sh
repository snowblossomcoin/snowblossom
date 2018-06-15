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

cd -

copy_common () {
	cp ../../bazel-bin/SnowBlossomNode_deploy.jar "$1"/
	cp ../../bazel-bin/SnowBlossomClient_deploy.jar "$1"/
	cp ../../bazel-bin/SnowBlossomMiner_deploy.jar "$1"/
	cp ../../bazel-bin/PoolMiner_deploy.jar "$1"/
	mkdir -p "$1/configs"

	# just copy configs
	#cp -r configs "$1"/
	# OR
	# convert line endings to make easily windows editable -_-;
	for i in configs/*; do awk 'sub("$", "\r")' "$i" > "$1/$i"; done
}

name="snowblossom-$version"
cp -r "snowblossom" "$name"
copy_common "$name"
zip -r -9 "$name.zip" "$name"
tar -czf "$name.tar.gz" "$name"
rm -r "$name"
