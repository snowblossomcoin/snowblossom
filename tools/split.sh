#!/bin/bash


for i in `seq 0 127`
do

  skip=$((1024 * i))
  name=$(printf '%x\n' $i)
  while [ ${#name} -lt 4 ]
  do
    name="0${name}"
  done
  echo $name
  dd if=snowblossom.7.snow of=snowblossom.7.snow.$name skip=$skip bs=1024k count=1024

done
