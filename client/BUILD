package(default_visibility = ["//visibility:public"])

java_library(
  name = "client",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
    "//protolib:protolib",
    "//lib:lib",
    "@commons_codec//jar",
    "@commons_math3//jar",
    "@bcprov//jar",
    "@junit_junit//jar",
    "@org_rocksdb_rocksdbjni//jar",
    "@duckutil//:duckutil_lib",
    "@duckutil//:lobstack_lib",
    "@jsonrpc2_server//jar",
    "@jsonrpc2_base//jar",
    "@json_smart//jar",
    "@accessors_smart//jar",
    "@asm//jar",
    ],
)

java_test(
    name = "client.test",
    test_class = "client.test.AllTests",
    srcs = glob(["test/**/*.java", "test/*.java"]),
    deps = [
		    "//client:client",
        "//lib:lib",
        "//protolib:protolib",
        "@duckutil//:duckutil_lib",
    ]
)
