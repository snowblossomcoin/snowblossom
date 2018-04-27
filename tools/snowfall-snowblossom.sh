#!/bin/bash

set -eu

if [ $# -ne 1 ]
then
  echo "syntax: $0 directory"
  exit 5
fi
 
BASE=$1

mkdir -p $BASE

for n in 0 1 2 3 4 5 6 7 8 9
do
  size=$(echo "2 $n ^ 1024 * p" |dc)
  mkdir -p $BASE/snowblossom.$n
  bazel run :SnowFall $BASE/snowblossom.$n/snowblossom.$n.snow snowblossom.$n $size
  hash=$(bazel-bin/SnowMerkle $BASE/snowblossom.$n snowblossom.$n)
  echo "$n:$hash" | tee -a $BASE/hashes-snowblossom.txt
done

