load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository") 
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

RULES_JVM_EXTERNAL_TAG = "3.0"
RULES_JVM_EXTERNAL_SHA = "62133c125bf4109dfd9d2af64830208356ce4ef8b165a6ef15bbff7460b35c3a"

git_repository(
    name = "rules_jvm_external",
    remote = "https://github.com/bazelbuild/rules_jvm_external",
    commit = "9aec21a7eff032dfbdcf728bb608fe1a02c54124",
    shallow_since = "1577467222 -0500"
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

git_repository(
  name = "build_stack_rules_proto",
  remote = "https://github.com/fireduck64/rules_proto",
  commit = "8ab7bf0c7c992c893813f7151af4794ec5dd3e3f",
  shallow_since = "1579204983 -0800"
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
  commit = "e022564cedb0d241c8e9a297eca74f6d064f7aa8",
  shallow_since = "1576867546 -0800",
)

maven_install(
    artifacts = [
				"com.google.protobuf:protobuf-java:3.5.1",
				"org.rocksdb:rocksdbjni:5.14.2",
        "junit:junit:4.12",
				"commons-codec:commons-codec:1.11",
        "org.apache.commons:commons-math3:3.6.1",
				"io.netty:netty-tcnative-boringssl-static:2.0.25.Final",
				"org.bouncycastle:bcprov-jdk15on:1.64",
        "org.bouncycastle:bcpkix-jdk15on:1.64",
				"com.madgag.spongycastle:prov:1.58.0.0",
				"com.thetransactioncompany:jsonrpc2-server:1.11",
				"net.minidev:json-smart:2.3",
				"com.lambdaworks:scrypt:1.4.0",
				"com.google.zxing:javase:3.4.0",
				"org.slf4j:slf4j-nop:1.7.25",
				"org.bitcoinj:bitcoinj-core:0.14.7",
    ],
    repositories = [
        "https://repo1.maven.org/maven2",
        "https://maven.google.com",
    ],
    maven_install_json = "//:maven_install.json",

)

load("@maven//:defs.bzl", "pinned_maven_install")
pinned_maven_install()


