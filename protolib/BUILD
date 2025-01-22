package(default_visibility = ["//visibility:public"])

load("@rules_proto_grpc_java//:defs.bzl", "java_grpc_library")
load("@rules_proto//proto:defs.bzl", "proto_library")


proto_library(
  name = "protosrc",
  srcs = glob(["*.proto"]),
  visibility = [
    "//visibility:public",
  ],
)

java_grpc_library(
  name = "protogrpc",
  protos = [":protosrc"],
)
