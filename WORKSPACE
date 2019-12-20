load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository") 
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

RULES_JVM_EXTERNAL_TAG = "3.0"
RULES_JVM_EXTERNAL_SHA = "62133c125bf4109dfd9d2af64830208356ce4ef8b165a6ef15bbff7460b35c3a"

http_archive(
    name = "rules_jvm_external",
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    sha256 = RULES_JVM_EXTERNAL_SHA,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

http_archive(
    name = "build_stack_rules_proto",
    urls = ["https://github.com/stackb/rules_proto/archive/78d64b7317a332ee884ad7fcd0506d78f2a402cb.tar.gz"],
    sha256 = "7f7fc55f1cfe8b28f95f1feb8ea42f21310cbbf3c1ee5015dfc15c604f6593f1",
    strip_prefix = "rules_proto-78d64b7317a332ee884ad7fcd0506d78f2a402cb",
)

load("@build_stack_rules_proto//:deps.bzl", "io_grpc_grpc_java")

io_grpc_grpc_java()

load("@io_grpc_grpc_java//:repositories.bzl", "grpc_java_repositories")

grpc_java_repositories(omit_com_google_protobuf = True)

load("@build_stack_rules_proto//java:deps.bzl", "java_grpc_library")

java_grpc_library()

git_repository(
  name = "duckutil",
  remote = "https://github.com/fireduck64/duckutil",
  commit = "74d770c50b407ca91416206931ceb5465d50776f",
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


