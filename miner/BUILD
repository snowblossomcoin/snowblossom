package(default_visibility = ["//visibility:public"])

java_library(
  name = "miner",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
      "//protolib:protogrpc",
      "//protolib:snow_java_proto",
      "//lib:lib",
      "//lib:rocksdb",
      "//lib:atomicfiledb",
      "//client",
      "@duckutil//:duckutil_jsonrpc_lib",
      "@duckutil//:duckutil_lib",
      "@maven//:com_thetransactioncompany_jsonrpc2_base",
      "@maven//:com_thetransactioncompany_jsonrpc2_server",
      "@maven//:junit_junit",
      "@maven//:net_minidev_json_smart",
      "@maven//:io_grpc_grpc_netty",
      "@maven//:io_netty_netty_handler",
      "@maven//:com_google_guava_guava",
      "@maven//:com_google_protobuf_protobuf_java",
      "@maven//:io_grpc_grpc_api",
      "@maven//:io_grpc_grpc_stub",
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
