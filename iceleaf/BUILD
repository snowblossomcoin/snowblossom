package(default_visibility = ["//visibility:public"])

java_library(
  name = "iceleaf",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
    "//protolib:protogrpc",
    "//lib:lib",
    "//client:client",
    "//node:node",
    "@duckutil//:duckutil_lib",
    ],
)

filegroup(
  name = "resources",
  srcs = glob(["resources/*"]),
)

