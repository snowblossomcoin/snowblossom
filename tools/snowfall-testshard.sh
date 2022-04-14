#!/bin/bash

set -eu

if [ $# -ne 1 ]
then
  echo "syntax: $0 directory"
  exit 5
fi
 
BASE=$1

mkdir -p $BASE

for n in 0 1 2 3 4 5 6 7 8 9 10
do
  size=$(echo "2 $n ^ p" |dc)
  mkdir -p $BASE/testshard.$n
  bazel run :SnowFall $BASE/testshard.$n/testshard.$n.snow testshard.$n $size
  hash=$(bazel-bin/SnowMerkle $BASE/testshard.$n testshard.$n)
  echo "$n:$hash" | tee -a $BASE/hashes-testshard.txt
done

