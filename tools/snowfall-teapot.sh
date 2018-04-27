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
  mkdir -p $BASE/teapot.$n
  bazel run :SnowFall $BASE/teapot.$n/teapot.$n.snow teapot.$n $size
  hash=$(bazel-bin/SnowMerkle $BASE/teapot.$n teapot.$n)
  echo "$n:$hash" | tee -a $BASE/hashes-teapot.txt
done

