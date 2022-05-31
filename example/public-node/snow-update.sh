#!/bin/bash

node_image=snowblossom/node:latest
explorer_image=snowblossom/explorer:latest

docker container stop snowblossom.explorer
docker container rm snowblossom.explorer

docker container stop -t 90 snowblossom.node
docker container rm snowblossom.node

docker volume create snownode
docker volume create snowexplore

docker run -d --pull always --restart always --name snowblossom.node --network host \
  -v snownode:/data -e snowblossom_addr_index=true -e snowblossom_tx_index=true $node_image

docker run -d --pull always --restart always --name snowblossom.explorer --network host \
  -v snowexplore:/data $explorer_image

