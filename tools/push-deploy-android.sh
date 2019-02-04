#!/bin/bash

set -eu
bazel build :SnowBlossomClient_deploy.jar

rm -rf t/snowjar
mkdir -p t/snowjar
cd t/snowjar

jar xvf ../../bazel-bin/SnowBlossomClient_deploy.jar
rm module-info.class
rm librocksdbjni*

rm -f ../SnowBlossomClient_android.jar

jar cvf ../SnowBlossomClient_android.jar .



