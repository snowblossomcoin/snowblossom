package(default_visibility = ["//visibility:public"])

load("@com_google_protobuf//bazel:proto_library.bzl", "proto_library")
load("@io_grpc_grpc_java//:java_grpc_library.bzl", "java_grpc_library")
load("@com_google_protobuf//bazel:java_proto_library.bzl", "java_proto_library")


proto_library(
  name = "snow_proto",
  srcs = glob(["*.proto"]),
  visibility = [
    "//visibility:public",
  ],
)

java_proto_library(
    name = "snow_java_proto",
    deps = [":snow_proto"],
)

java_grpc_library(
  name = "protogrpc",
  srcs = [":snow_proto"],
  deps = [":snow_java_proto"],
)
