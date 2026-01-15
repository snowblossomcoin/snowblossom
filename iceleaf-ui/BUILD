package(default_visibility = ["//visibility:public"])

java_library(
  name = "iceleaf",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
    "//protolib:protogrpc",
    "//protolib:snow_java_proto",
    "//lib:lib",
    "//client:client",
    "//node:node",
    "@duckutil//:duckutil_lib",
    "@maven//:com_google_zxing_javase",
    "@maven//:com_google_zxing_core",
    "@maven//:com_google_guava_guava",
    "@maven//:com_google_protobuf_protobuf_java",
    "@maven//:io_grpc_grpc_api",
    "@maven//:io_grpc_grpc_stub",
    ],
)

filegroup(
  name = "resources",
  srcs = glob(["resources/**"]),
)

