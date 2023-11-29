load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository") 
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

RULES_JVM_EXTERNAL_TAG = "3.0"
RULES_JVM_EXTERNAL_SHA = "62133c125bf4109dfd9d2af64830208356ce4ef8b165a6ef15bbff7460b35c3a"

git_repository(
    name = "rules_jvm_external",
    remote = "https://github.com/bazelbuild/rules_jvm_external",
    commit = "ec2c5617b339844312d4adef4400dcc2ccb73c4f",
    shallow_since = "1614596935 +0000"
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

git_repository(
  name = "build_stack_rules_proto",
  remote = "https://github.com/fireduck64/rules_proto",
  commit = "3e0b10c45c5e15b3ee17b3aa8a7ffe6e16b018cc",
  shallow_since = "1614632955 -0800"
)

load("@build_stack_rules_proto//:deps.bzl", "io_grpc_grpc_java")
load("@build_stack_rules_proto//java:deps.bzl", "java_proto_compile")

io_grpc_grpc_java()
java_proto_compile()

load("@io_grpc_grpc_java//:repositories.bzl", "grpc_java_repositories")

grpc_java_repositories()

load("@build_stack_rules_proto//java:deps.bzl", "java_grpc_library")

java_grpc_library()

git_repository(
  name = "duckutil",
  remote = "https://github.com/fireduck64/duckutil",
  commit = "b721d945278dc2069bb9f5c1556161b37d6b4ee8",
  shallow_since = "1648055343 -0700"
)

maven_install(
    artifacts = [
				"com.google.protobuf:protobuf-java:3.5.1",
				"org.rocksdb:rocksdbjni:7.2.2",
        "junit:junit:4.12",
				"commons-codec:commons-codec:1.11",
        "org.apache.commons:commons-math3:3.6.1",
				"io.netty:netty-tcnative-boringssl-static:2.0.25.Final",
				"org.bouncycastle:bcprov-jdk18on:1.77",
        "org.bouncycastle:bcpkix-jdk18on:1.77",
				"com.thetransactioncompany:jsonrpc2-server:1.11",
				"net.minidev:json-smart:2.4.7",
				"com.lambdaworks:scrypt:1.4.0",
				"com.google.zxing:javase:3.4.0",
				"org.slf4j:slf4j-nop:1.7.25",
				"org.bitcoinj:bitcoinj-core:0.15.10",
        "org.bitlet:weupnp:0.1.4",
    ],
    repositories = [
        "https://repo1.maven.org/maven2",
        "https://maven.google.com",
    ],
    maven_install_json = "//:maven_install.json",

)
# After updating run:
#
# bazel run @unpinned_maven//:pin
#
# See: https://github.com/bazelbuild/rules_jvm_external


load("@maven//:defs.bzl", "pinned_maven_install")
pinned_maven_install()


