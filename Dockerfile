FROM l.gcr.io/google/bazel as build
# mkdir line debian/openjdk bug https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=863199#23
COPY .git /snowblossom/.git
RUN \
  set -eux && \
  mkdir -p /usr/share/man/man1 && \
  add-apt-repository ppa:openjdk-r/ppa && \
  apt-get update -q && \
  apt-get install -yqq --no-install-recommends openjdk-11-jdk-headless && \
  cd /snowblossom && \
  git checkout . && \
  bazel build :Everything_deploy.jar  


FROM openjdk:11-slim as run
COPY --from=build /snowblossom/bazel-bin/Everything_deploy.jar /snowblossom/
COPY example/deployment/docker/scripts/run /snowblossom/scripts
COPY example/configs/logging.properties /snowblossom/log.conf
RUN \
  apt-get update -q && \
  apt-get -yqq install sudo && \
  apt-get clean


VOLUME ["/data"]
ENTRYPOINT ["/bin/sh", "/snowblossom/scripts/entrypoint"]
CMD ["node"]
