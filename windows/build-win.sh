#!/bin/sh

set -eu
rm -f snowblossom-windows.zip
cd ..
bazel build :SnowBlossomMiner_deploy.jar :SnowBlossomNode_deploy.jar :SnowBlossomClient_deploy.jar

rsync -avP bazel-bin/*_deploy.jar windows/snowblossom/

cd windows
zip -r -9 snowblossom-windows.zip snowblossom


