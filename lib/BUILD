package(default_visibility = ["//visibility:public"])

java_library(
  name = "lib",
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
    "@duckutil//:duckutil_jsonrpc_lib",
    "@jsonrpc2_base//jar",
    "@jsonrpc2_server//jar",
    "@json_smart//jar",
    ],
)

java_test(
    name = "lib.test",
    test_class = "lib.test.AllTests",
    srcs = glob(["test/**/*.java", "test/*.java"]),
    deps = [
        "//lib:lib",
        "//protolib:protolib",
        "@bcprov//jar",
        "@commons_codec//jar",
        "@duckutil//:duckutil_lib"
    ]
)
