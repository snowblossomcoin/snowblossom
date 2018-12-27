package(default_visibility = ["//visibility:public"])

java_library(
  name = "node",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
      "//protolib:protogrpc",
      "@commons_codec//jar",
      "@commons_math3//jar",
      "@junit_junit//jar",
      "@org_rocksdb_rocksdbjni//jar",
      "@duckutil//:duckutil_lib",
      "@duckutil//:lobstack_lib",
      "//lib:lib",
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
