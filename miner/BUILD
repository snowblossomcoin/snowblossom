package(default_visibility = ["//visibility:public"])

java_library(
  name = "miner",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
      "//protolib:protogrpc",
      "@commons_codec//jar",
      "@commons_math3//jar",
      "@bcprov//jar",
      "@junit_junit//jar",
      "@org_rocksdb_rocksdbjni//jar",
      "@duckutil//:duckutil_lib",
      "@duckutil//:duckutil_jsonrpc_lib",
      "@jsonrpc2_base//jar",
      "@jsonrpc2_server//jar",
      "@json_smart//jar",
      "@duckutil//:lobstack_lib",
      "//lib",
      "//client",
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
