ARG BAZEL_VERSION=2.0.0
ARG BAZEL_OPTIONS


FROM debian:10-slim as build
ARG BAZEL_VERSION
ARG BAZEL_OPTIONS
RUN printenv
RUN exit
# mkdir line debian/openjdk bug https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=863199#23
RUN \
  mkdir -p /usr/share/man/man1 && \
  apt-get update -q && \
  apt-get install -yqq --no-install-recommends curl gnupg2 git openjdk-11-jdk-headless && \
  APT_KEY_DONT_WARN_ON_DANGEROUS_USAGE=DontWarn && \
  curl -s https://bazel.build/bazel-release.pub.gpg | apt-key add - && \
  echo "deb [arch=amd64] https://storage.googleapis.com/bazel-apt stable jdk1.8" > /etc/apt/sources.list.d/bazel.list && \
  apt-get update -q && \
  apt-get install -yq "bazel-${BAZEL_VERSION}"

COPY .git /snowblossom/.git
RUN \
  cd /snowblossom && \
  git checkout . && \
  bazel-${BAZEL_VERSION} build ${BAZEL_OPTIONS} :Everything_deploy.jar  


FROM openjdk:11-slim as run
COPY --from=build /snowblossom/bazel-bin/Everything_deploy.jar /snowblossom/
COPY example/deployment/docker/scripts/run /snowblossom/scripts
COPY example/configs/logging.properties /snowblossom/log.conf
RUN \
  apt-get update -q && \
  apt-get -yqq install sudo && \
  apt-get clean


VOLUME [/data]
ENTRYPOINT ["/bin/sh", "/snowblossom/scripts/entrypoint"]
CMD ["node"]
