
#note:  this is a multistage dockerfile
#this is a new concept (new as of 2017) and requires docker version 17.05 or higher

#*** first stage is for building

FROM ubuntu:18.10 as builder

RUN apt-get update
RUN apt-get install --yes wget gpg
RUN echo "deb [arch=amd64] http://storage.googleapis.com/bazel-apt stable jdk1.8" > /etc/apt/sources.list.d/snowblossom-bazel.list
RUN wget --output-document=bazel-release.pub.gpg --quiet https://bazel.build/bazel-release.pub.gpg && apt-key add bazel-release.pub.gpg && rm --force bazel-release.pub.gpg
RUN apt-get update
RUN apt-get install --no-install-recommends --yes openjdk-8-jdk-headless bazel

RUN groupadd --gid 61000 docker
RUN useradd --create-home --uid 61000 --gid docker --shell /bin/bash docker
USER docker

WORKDIR /home/docker
ADD --chown=docker:docker . snowblossom
WORKDIR /home/docker/snowblossom
RUN bazel build :SnowBlossomMiner_deploy.jar \
  :SnowBlossomNode_deploy.jar \
  :SnowBlossomClient_deploy.jar \
  :PoolMiner_deploy.jar \
  :SnowFall_deploy.jar \
  :Arktika_deploy.jar \
  :ShackletonExplorer_deploy.jar


#*** second stage is for running

FROM ubuntu:18.10

EXPOSE 2338

RUN apt-get update && \
  apt-get install --no-install-recommends --yes openjdk-8-jre-headless && \
  apt-get clean

RUN groupadd --gid 61000 docker
RUN useradd --create-home --uid 61000 --gid docker --shell /bin/bash docker
USER docker

RUN mkdir /home/docker/snowblossom
WORKDIR /home/docker/snowblossom
COPY --chown=docker:docker --from=builder \
  /home/docker/snowblossom/bazel-bin/SnowBlossomMiner_deploy.jar \
  /home/docker/snowblossom/bazel-bin/SnowBlossomNode_deploy.jar \
  /home/docker/snowblossom/bazel-bin/SnowBlossomClient_deploy.jar \
  /home/docker/snowblossom/bazel-bin/PoolMiner_deploy.jar \
  /home/docker/snowblossom/bazel-bin/SnowFall_deploy.jar \
  /home/docker/snowblossom/bazel-bin/Arktika_deploy.jar \
  /home/docker/snowblossom/bazel-bin/ShackletonExplorer_deploy.jar \
  ./

RUN echo '#!/bin/bash\njava -jar SnowBlossomNode_deploy.jar configs/node.conf "$@"' > node.sh && \
  echo '#!/bin/bash\njava -jar SnowBlossomClient_deploy.jar configs/client.conf "$@"' > client.sh && \
  echo '#!/bin/bash\njava -jar SnowBlossomMiner_deploy.jar configs/miner.conf "$@"' > miner.sh && \
  echo '#!/bin/bash\njava -jar PoolMiner_deploy.jar configs/pool-miner.conf "$@"' > pool-miner.sh && \
  echo '#!/bin/bash\njava -Xmx50g -jar Arktika_deploy.jar "$@"' > arktika.sh && \
  echo '#!/bin/bash\njava -Xmx10g -jar Arktika_deploy.jar "$@"' > arktika-small.sh && \
  echo '#!/bin/bash\njava -Xmx10g -jar ShackletonExplorer_deploy.jar configs/explorer.conf "$@"' > shackleton-explorer.sh && \
  chmod +x *.sh && \
  mkdir -p "logs"

CMD ./node.sh

#to build:  docker build --tag=snowblossom .
#to run: cp --recursive example/configs configs
#  mkdir -p node_db wallet snow
#  chmod go+wt node_db wallet snow

#use --restart=always --detach instead of --rm if you want this to start on boot
#to run       node: docker run --rm --name=snowblossom-node --volume $PWD/configs:/home/docker/snowblossom/configs --volume $PWD/node_db:/home/docker/snowblossom/node_db --publish 2338:2338 snowblossom
#to run     client: docker run --rm --name=snowblossom-client --network=host --volume $PWD/configs:/home/docker/snowblossom/configs --volume $PWD/wallet:/home/docker/snowblossom/wallet snowblossom ./client.sh
#to run      miner: docker run --rm --name=snowblossom-miner --network=host --volume $PWD/configs:/home/docker/snowblossom/configs --volume $PWD/snow:/home/docker/snowblossom/snow --volume $PWD/wallet:/home/docker/snowblossom/wallet snowblossom ./miner.sh
#to run  poolminer: docker run --rm --name=snowblossom-pool-miner --network=host --volume $PWD/configs:/home/docker/snowblossom/configs --volume $PWD/snow:/home/docker/snowblossom/snow --volume $PWD/wallet:/home/docker/snowblossom/wallet snowblossom ./pool-miner.sh
#to run    arktika: docker run --rm --name=snowblossom-arktika --network=host --volume $PWD/configs:/home/docker/snowblossom/configs --volume $PWD/snow:/home/docker/snowblossom/snow --volume $PWD/wallet:/home/docker/snowblossom/wallet snowblossom ./arktika-small.sh configs/node1.conf
#to run shackleton: docker run --rm --name=snowblossom-shackleton-explorer --network=host --volume $PWD/configs:/home/docker/snowblossom/configs --publish 8080:8080 snowblossom ./shackleton-explorer.sh
#to run   snowfall: n=8; mkdir -p snow/snowblossom.$n && chmod go+wt snow/snowblossom.$n && docker run --rm --name=snowblossom-snowfall --network=host --volume $PWD/configs:/home/docker/snowblossom/configs --volume $PWD/snow:/home/docker/snowblossom/snow --volume $PWD/wallet:/home/docker/snowblossom/wallet snowblossom java -jar SnowFall_deploy.jar snow/snowblossom.$n/snowblossom.$n.snow snowblossom.$n $(dc <<< "2 $n ^ 1024 * p")

