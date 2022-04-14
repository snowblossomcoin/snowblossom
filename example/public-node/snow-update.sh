#!/bin/bash

image=snowblossom/snowblossom:latest
#image=snowlocal

docker pull $image

docker container stop snowblossom.explorer
docker container rm snowblossom.explorer

docker container stop -t 90 snowblossom.node
docker container rm snowblossom.node

docker volume create snownode
docker volume create snowexplore

docker run -d --restart always --name snowblossom.node --network host \
  -v snownode:/data $image

docker run -d --restart always --name snowblossom.explorer --network host \
  -v snowexplore:/data $image explorer


