package(default_visibility = ["//visibility:public"])

java_library(
  name = "miner",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
      "//protolib:protogrpc",
      "//lib:lib",
      "//lib:rocksdb",
      "//lib:atomicfiledb",
      "//client",
      "@io_grpc_grpc_java//context",
      "@duckutil//:duckutil_jsonrpc_lib",
      "@duckutil//:duckutil_lib",
      "@maven//:com_thetransactioncompany_jsonrpc2_base",
      "@maven//:com_thetransactioncompany_jsonrpc2_server",
      "@maven//:junit_junit",
      "@maven//:net_minidev_json_smart",
    ],
)

java_test(
    name = "miner.test.ShareManagerTest",
    test_class = "miner.test.ShareManagerTest",
    srcs = glob(["test/ShareManagerTest.java"]),
    deps = [
		    "//miner:miner",
        "//lib:lib",
        "//protolib:protogrpc",
    ]
)
java_test(
    name = "miner.test.SnowMerkleProofTest",
    test_class = "miner.test.SnowMerkleProofTest",
    srcs = glob(["test/SnowMerkleProofTest.java"]),
    deps = [
		    "//miner:miner",
        "//lib:lib",
        "//protolib:protogrpc",
    ]
)
java_test(
    name = "miner.test.FaQueueTest",
    test_class = "miner.test.FaQueueTest",
    srcs = glob(["test/FaQueueTest.java"]),
    deps = [
		    "//miner:miner",
        "//lib:lib",
        "//protolib:protogrpc",
    ]
)
