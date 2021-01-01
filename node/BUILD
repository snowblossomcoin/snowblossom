package(default_visibility = ["//visibility:public"])

java_library(
  name = "node",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
      "//protolib:protogrpc",
      "@duckutil//:duckutil_lib",
      "@duckutil//:lobstack_lib",
      "//lib:lib",
      "//lib:rocksdb",
      "//lib:atomicfiledb",
      "//client:client",
      "@io_netty_netty_handler//:io_netty_netty_handler",
      "@io_grpc_grpc_java//netty",
      "@maven//:junit_junit",
    ],
)

java_test(
    name = "node.test",
    test_class = "node.test.AllTests",
    srcs = glob(["test/**/*.java", "test/*.java"]),
    deps = [
		    "//node:node",
        "//lib:lib",
        "//protolib:protogrpc",
        "@duckutil//:duckutil_lib",
    ]
)
