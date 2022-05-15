ARG DISTRO=debian
ARG DISTRO_VERSION=bullseye-slim


FROM $DISTRO:$DISTRO_VERSION as base
# https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=863199#23
RUN mkdir -p /usr/share/man/man1


FROM base as build-dependencies
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get -qq update \
  && apt-get -qq upgrade \
  && apt-get -qq install --no-install-suggests --no-install-recommends \
    git \
    bazel-bootstrap \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*


FROM build-dependencies as build
WORKDIR /snowblossom/
ARG GIT_REF=master
RUN git init . \
  && git remote add origin https://github.com/snowblossomcoin/snowblossom.git \
  && git fetch origin $GIT_REF \
  && git checkout FETCH_HEAD \
  && rm -rf .git/
RUN bazel build :Everything_deploy.jar


FROM base as runtime-dependencies
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get -qq update \
  && apt-get -qq upgrade \
  && apt-get -qq install --no-install-suggests --no-install-recommends \
    openjdk-17-jre-headless \
    fontconfig \
    libfreetype6 \
    libxext6 \
    libxrender1 \
    libxtst6 \
    sudo \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*


FROM runtime-dependencies as runtime
WORKDIR /snowblossom/
COPY --from=build /snowblossom/bazel-bin/Everything_deploy.jar .
COPY --from=build /snowblossom/example/deployment/docker/scripts/* ./scripts/
COPY --from=build /snowblossom/example/configs/logging.properties log.conf


VOLUME ["/data"]
ENTRYPOINT ["/bin/sh", "/snowblossom/scripts/entrypoint"]
CMD ["node"]
