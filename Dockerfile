ARG BAZEL_VERSION=2.0.0
ARG BAZEL_OPTIONS

FROM debian:10-slim as build
ARG BAZEL_VERSION=${BAZEL_VERSION}
ARG BAZEL_OPTIONS=${BAZEL_OPTIONS}
COPY . /snowblossom
RUN /bin/bash /snowblossom/example/deployment/docker/scripts/build/prepare-build-environment
RUN /bin/bash /snowblossom/example/deployment/docker/scripts/build/build-snowblossom

FROM openjdk:11-slim as run
COPY --from=build /snowblossom/bazel-bin/Everything_deploy.jar /snowblossom/
COPY example/deployment/docker/scripts/run /snowblossom/scripts
COPY example/configs/logging.properties /snowblossom/log.conf
RUN /bin/sh /snowblossom/scripts/prepare-run-environment

VOLUME [/data]
ENTRYPOINT ["/bin/sh", "/snowblossom/scripts/entrypoint"]
CMD ["node"]
