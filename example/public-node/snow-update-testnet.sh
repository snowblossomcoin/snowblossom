#!/bin/bash

node_image=snowblossom/node:latest
explorer_image=snowblossom/explorer:latest

docker pull $node_image
docker pull $explorer_image

docker container stop snowblossom.test.explorer
docker container rm snowblossom.test.explorer

docker container stop -t 90 snowblossom.test.node
docker container rm snowblossom.test.node


docker volume create snownode.test
docker volume create snowexplore.test

docker run -d --restart always --name snowblossom.test.node --network host \
  -e snowblossom_network=testnet \
  -e snowblossom_service_port=2339 \
  -e snowblossom_tls_service_port=2349 \
  -v snownode.test:/data $node_image


docker run -d --restart always --name snowblossom.test.explorer --network host \
  -e snowblossom_network=testnet \
  -e snowblossom_port=8888 \
  -e snowblossom_node_uri=grpc://localhost:2339 \
  -v snowexplore.test:/data $explorer_image


