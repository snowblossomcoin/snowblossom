load("@bazel_tools//tools/build_defs/repo:git.bzl", "git_repository") 
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

git_repository(
  name = "duckutil",
  remote = "https://github.com/fireduck64/duckutil",
  tag = "v1.1.0",
)

maven_jar(
  name = "protobuf",
  artifact = "com.google.protobuf:protobuf-java:3.5.1",
  sha1 = "8c3492f7662fa1cbf8ca76a0f5eb1146f7725acd",
)

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

maven_jar(
  name = "org_rocksdb_rocksdbjni",
  artifact = "org.rocksdb:rocksdbjni:5.14.2",
  sha1 = "a6087318fab540ba0b4c6ff68475ffbedc0b3d10",
)

maven_jar(
  name = "weupnp",
  artifact = "org.bitlet:weupnp:0.1.4",
  sha1 = "b99cd791ede89b7c17426e6c51a0f171dc925def",
)

maven_jar(
  name = "commons_codec",
  artifact = "commons-codec:commons-codec:1.11",
  sha1 = "3acb4705652e16236558f0f4f2192cc33c3bd189",
)

maven_jar(
  name = "commons_io",
  artifact = "commons-io:commons-io:2.6",
  sha1 = "815893df5f31da2ece4040fe0a12fd44b577afaf",
)
maven_jar(
  name = "commons_math3",
  artifact = "org.apache.commons:commons-math3:3.6.1",
  sha1 = "e4ba98f1d4b3c80ec46392f25e094a6a2e58fcbf",
)
maven_jar(
  name = "netty_tcnative",
  artifact = "io.netty:netty-tcnative-boringssl-static:2.0.25.Final",
  sha1 = "185980556f9f083b5339825f19c9641c6c879417",
)
maven_jar(
  name = "bcpkix",
  artifact = "org.bouncycastle:bcpkix-jdk15on:1.60",
  sha1 = "d0c46320fbc07be3a24eb13a56cee4e3d38e0c75",
)
maven_jar(
  name = "bcprov",
  artifact = "org.bouncycastle:bcprov-jdk15on:1.60",
  sha1 = "bd47ad3bd14b8e82595c7adaa143501e60842a84",
)
maven_jar(
  name = "scprov",
  artifact = "com.madgag.spongycastle:prov:1.58.0.0",
  sha1 = "2e2c2f624ed91eb40e690e3596c98439b1b50f2a",
)
maven_jar(
  name = "sccore",
  artifact = "com.madgag.spongycastle:core:1.58.0.0",
  sha1 = "e08789f8f1e74f155db8b69c3575b5cb213c156c",
)

maven_jar(
  name = "jsonrpc2_server",
  artifact = "com.thetransactioncompany:jsonrpc2-server:1.11",
  sha1 = "3f5866109d05f036bd12c7998d0b20166c656033",
)

maven_jar(
  name = "jsonrpc2_base",
  artifact = "com.thetransactioncompany:jsonrpc2-base:1.38.1",
  sha1 = "ba8da1486587870aa0eb2820b731e3ed6f8fa8a2",
)

maven_jar(
  name = "json_smart",
  artifact = "net.minidev:json-smart:2.3",
  sha1 = "007396407491352ce4fa30de92efb158adb76b5b",
)

maven_jar(
  name = "accessors_smart",
  artifact = "net.minidev:accessors-smart:1.2",
  sha1 = "c592b500269bfde36096641b01238a8350f8aa31",
)

maven_jar(
  name = "asm",
  artifact = "org.ow2.asm:asm:6.2",
  sha1 = "1b6c4ff09ce03f3052429139c2a68e295cae6604",
)

# Used for HD wallet and seed stuff only
maven_jar(
  name = "bitcoinj",
  artifact = "org.bitcoinj:bitcoinj-core:0.14.7",
  sha1 = "5e58d6921e1d8dfce81525b22c0de97f34be1f5c",
)
maven_jar(
  name = "slf4j_nop",
  artifact = "org.slf4j:slf4j-nop:1.7.25",
  sha1 = "8c7708c79afec923de8957b7d4f90177628b9fcd",
)
maven_jar(
  name = "slf4j_api",
  artifact = "org.slf4j:slf4j-api:1.7.25",
  sha1 = "da76ca59f6a57ee3102f8f9bd9cee742973efa8a",
)

