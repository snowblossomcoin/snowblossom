#!/bin/bash


for i in `seq 0 127`
do
  name=$(printf '%x\n' $i)
  while [ ${#name} -lt 4 ]
  do
    name="0${name}"
  done
  cat snowblossom.7.snow.$name


done
