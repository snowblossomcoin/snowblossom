#!/bin/bash

node_image=snowblossom/node:latest
explorer_image=snowblossom/explorer:latest

docker pull $node_image
docker pull $explorer_image

docker container stop snowblossom.explorer
docker container rm snowblossom.explorer

docker container stop -t 90 snowblossom.node
docker container rm snowblossom.node

docker volume create snownode
docker volume create snowexplore

docker run -d --restart always --name snowblossom.node --network host \
  -v snownode:/data $node_image

docker run -d --restart always --name snowblossom.explorer --network host \
  -v snowexplore:/data $explorer_image


