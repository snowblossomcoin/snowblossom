package(default_visibility = ["//visibility:public"])

java_library(
  name = "miner",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
      "//protolib:protogrpc",
      "//lib:lib",
      "//lib:rocksdb",
      "//client",
      "@duckutil//:duckutil_jsonrpc_lib",
      "@duckutil//:duckutil_lib",
      "@maven//:com_thetransactioncompany_jsonrpc2_base",
      "@maven//:com_thetransactioncompany_jsonrpc2_server",
      "@maven//:junit_junit",
      "@maven//:net_minidev_json_smart",
    ],
)

java_test(
    name = "miner.test",
    test_class = "miner.test.AllTests",
    srcs = glob(["test/**/*.java", "test/*.java"]),
    deps = [
		    "//miner:miner",
        "//lib:lib",
        "//protolib:protogrpc",
    ]
)
