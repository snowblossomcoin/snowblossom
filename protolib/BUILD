package(default_visibility = ["//visibility:public"])

load("@build_stack_rules_proto//java:java_grpc_library.bzl", "java_grpc_library")

proto_library(
  name = "protosrc",
  srcs = glob(["*.proto"]),
  visibility = [
    "//visibility:public",
  ],
)

java_grpc_library(
  name = "protogrpc",
  deps = [":protosrc"],
)
