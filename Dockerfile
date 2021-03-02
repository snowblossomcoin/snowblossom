FROM l.gcr.io/google/bazel as build
# mkdir line debian/openjdk bug https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=863199#23

RUN \
  set -eux && \
  mkdir -p /usr/share/man/man1 && \
  add-apt-repository ppa:openjdk-r/ppa && \
  apt-get update -q && \
  apt-get install -yqq --no-install-recommends openjdk-11-jdk-headless 

RUN git clone --depth 1 --branch master https://github.com/snowblossomcoin/snowblossom.git /snowblossom-cache
WORKDIR /snowblossom-cache
RUN bazel build :Everything_deploy.jar

COPY .git /snowblossom/.git

WORKDIR /snowblossom
RUN git checkout .
RUN bazel build :Everything_deploy.jar  

FROM openjdk:11-slim as run

RUN \
  apt-get update -q && \
  apt-get -yqq install sudo && \
  apt-get -yqq install libxext6 libxrender1 libxtst6 libfreetype6 fontconfig && \
  apt-get clean

COPY --from=build /snowblossom/bazel-bin/Everything_deploy.jar /snowblossom/
COPY example/deployment/docker/scripts/run /snowblossom/scripts
COPY example/configs/logging.properties /snowblossom/log.conf
VOLUME ["/data"]
ENTRYPOINT ["/bin/sh", "/snowblossom/scripts/entrypoint"]
CMD ["node"]
