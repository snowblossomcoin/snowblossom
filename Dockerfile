ARG BASE_IMAGE=debian:bullseye-slim
ARG GIT_REPO=https://github.com/snowblossomcoin/snowblossom.git
ARG GIT_REF=master


# prepares a build image that caches all bazel packages
# so they don't have to be redownloaded every commit
FROM $BASE_IMAGE as prepared-build-image
ARG GIT_REPO
ARG GIT_REF
ARG DEBIAN_FRONTEND=noninteractive
# mkdir line debian/openjdk bug https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=863199#23
RUN \
  mkdir -p /usr/share/man/man1 \
  && apt-get update -q \
  && apt-get install -yqq apt-utils 2>&1 \
  && apt-get install -qq --no-install-suggests --no-install-recommends \
    git \
    bazel-bootstrap \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*
RUN \
  git clone --depth 1 --branch "${GIT_REF}" "${GIT_REPO}" /snowblossom \
  && cd /snowblossom \
  && bazel build :Everything_deploy.jar


# prepares a run image
FROM $BASE_IMAGE as prepared-run-image
ARG DEBIAN_FRONTEND=noninteractive
RUN \
  mkdir -p /usr/share/man/man1 \
  && apt-get update -q \
  && apt-get install -yqq apt-utils 2>&1 \
  && apt-get install -qq --no-install-suggests --no-install-recommends openjdk-11-jre-headless \
  && apt-get -yqq install \
    sudo \
    libxext6 \
    libxrender1 \
    libxtst6 \
    libfreetype6 \
    fontconfig \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*


# uses the build image with cached bazel packages to build only whats changed
FROM prepared-build-image as build
COPY .git /snowblossom/.git
RUN \
  cd /snowblossom \
  && git checkout . \
  && bazel build :Everything_deploy.jar


# uses the prepared run image deploy the new build
FROM prepared-run-image as run
WORKDIR /snowblossom/
COPY --from=build /snowblossom/bazel-bin/Everything_deploy.jar .
COPY example/deployment/docker/scripts/run ./scripts/
COPY example/configs/logging.properties log.conf

VOLUME ["/data"]
ENTRYPOINT ["/bin/sh", "/snowblossom/scripts/entrypoint"]
CMD ["node"]
