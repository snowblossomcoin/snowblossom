#!/bin/bash


docker container stop snowblossom.explorer
docker container rm snowblossom.explorer

docker container stop -t 30 snowblossom.node
docker container rm snowblossom.node
