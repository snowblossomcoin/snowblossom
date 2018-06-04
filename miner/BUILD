package(default_visibility = ["//visibility:public"])

java_library(
  name = "miner",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
      "//protolib:protolib",
      "@commons_codec//jar",
      "@commons_math3//jar",
      "@bcprov//jar",
      "@junit_junit//jar",
      "@org_rocksdb_rocksdbjni//jar",
      "@duckutil//:duckutil_lib",
      "@duckutil//:lobstack_lib",
      "//lib:lib",
    ],
)

java_test(
    name = "miner.test",
    test_class = "miner.test.AllTests",
    srcs = glob(["test/**/*.java", "test/*.java"]),
    deps = [
		    "//miner:miner",
        "//lib:lib",
        "//protolib:protolib",
    ]
)