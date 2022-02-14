FROM debian:testing as prepared-build-image
ARG DEBIAN_FRONTEND=noninteractive
# mkdir line debian/openjdk bug https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=863199#23
RUN \
mkdir -p /usr/share/man/man1 && \
apt-get update -q && \
apt-get install -yqq apt-utils 2>&1 && \
apt-get install -qq --no-install-suggests --no-install-recommends git bazel-bootstrap && \
apt-get clean
RUN \
git clone --depth 1 --branch master https://github.com/snowblossomcoin/snowblossom.git /snowblossom && \
cd /snowblossom && \
bazel build :Everything_deploy.jar


FROM debian:testing as prepared-run-image
ARG DEBIAN_FRONTEND=noninteractive
RUN \
mkdir -p /usr/share/man/man1 && \
apt-get update -q && \
apt-get install -yqq apt-utils 2>&1 && \
apt-get install -qq --no-install-suggests --no-install-recommends openjdk-17-jre-headless && \
apt-get -yqq install sudo libxext6 libxrender1 libxtst6 libfreetype6 fontconfig && \
apt-get clean


FROM prepared-build-image as build
COPY .git /snowblossom/.git
RUN \
cd /snowblossom && \
git checkout . && \
bazel build :Everything_deploy.jar


FROM prepared-run-image as run
COPY --from=build /snowblossom/bazel-bin/Everything_deploy.jar /snowblossom/
COPY example/deployment/docker/scripts/run /snowblossom/scripts
COPY example/configs/logging.properties /snowblossom/log.conf

VOLUME ["/data"]
ENTRYPOINT ["/bin/sh", "/snowblossom/scripts/entrypoint"]
CMD ["node"]
