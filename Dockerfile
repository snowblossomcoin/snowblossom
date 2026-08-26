ARG DISTRO=debian
ARG DISTRO_VERSION=stable


FROM $DISTRO:$DISTRO_VERSION AS base
# https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=863199#23
RUN mkdir -p /usr/share/man/man1


FROM base AS build-dependencies
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get -qq update
RUN apt-get -qq upgrade
RUN apt-get -qq install --no-install-suggests --no-install-recommends \
    gnupg git curl default-jdk-headless wget
RUN apt-get -qq install gcc-14 g++-14 gcc g++
#RUN curl -fsSL https://bazel.build/bazel-release.pub.gpg | gpg --dearmor >bazel-archive-keyring.gpg
#RUN mv bazel-archive-keyring.gpg /usr/share/keyrings
#RUN echo "deb [arch=amd64 signed-by=/usr/share/keyrings/bazel-archive-keyring.gpg] https://storage.googleapis.com/bazel-apt stable jdk1.8" | tee /etc/apt/sources.list.d/bazel.list
#RUN apt-get update
#RUN apt-get install -y bazel
#RUN apt-get clean 
#RUN apt-get install -y bazel
RUN wget https://github.com/bazelbuild/bazelisk/releases/download/v1.29.0/bazelisk-amd64.deb
RUN dpkg -i bazelisk-amd64.deb

FROM build-dependencies AS build
WORKDIR /snowblossom/
ARG GIT_REF=master
RUN git init . \
  && git remote add origin https://github.com/snowblossomcoin/snowblossom.git \
  && git fetch origin $GIT_REF \
  && git checkout FETCH_HEAD \
  && rm -rf .git/
ENV USE_BAZEL_VERSION 7.7.1
ENV USE_BAZEL_VERSION 8.7.0
RUN bazel build :Everything_deploy.jar


FROM base AS runtime-dependencies
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get -qq update \
  && apt-get -qq upgrade \
  && apt-get -qq install --no-install-suggests --no-install-recommends \
    default-jdk-headless \
    fontconfig \
    libfreetype6 \
    libxext6 \
    libxrender1 \
    libxtst6 \
    sudo \
  && apt-get clean \
  && rm -rf /var/lib/apt/lists/*


FROM runtime-dependencies AS runtime
WORKDIR /snowblossom/
COPY --from=build /snowblossom/bazel-bin/Everything_deploy.jar .
COPY --from=build /snowblossom/example/deployment/docker/scripts/* ./scripts/
COPY --from=build /snowblossom/example/configs/logging.properties log.conf


VOLUME ["/data"]
ENTRYPOINT ["/bin/sh", "/snowblossom/scripts/entrypoint"]
CMD ["node"]
