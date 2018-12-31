package(default_visibility = ["//visibility:public"])

java_library(
  name = "client",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
    "//protolib:protogrpc",
    "//lib:lib",
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
    "@com_google_protobuf//:protobuf_java_util",
    "@bitcoinj//jar",
    "@slf4j_nop//jar",
    "@slf4j_api//jar",
    ],
)

java_test(
    name = "client.test",
    test_class = "client.test.AllTests",
    srcs = glob(["test/**/*.java", "test/*.java"]),
    deps = [
		    "//client:client",
        "//lib:lib",
        "//protolib:protogrpc",
        "@duckutil//:duckutil_lib",
        "@bitcoinj//jar",
    ]
)
