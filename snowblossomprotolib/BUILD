package(default_visibility = ["//visibility:public"])

load("@org_pubref_rules_protobuf//java:rules.bzl", "java_proto_library")

java_proto_library(
  name = "snowblossomprotolib",
  protos = glob(["*.proto"]),
  with_grpc = True,
  verbose = 1,
)