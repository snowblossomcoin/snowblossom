package(default_visibility = ["//visibility:public"])

java_library(
  name = "lib",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
    "//protolib:protogrpc",
    "@commons_codec//jar",
    "@commons_math3//jar",
    "@bcprov//jar",
    "@scprov//jar",
    "@sccore//jar",
    "@junit_junit//jar",
    "@org_rocksdb_rocksdbjni//jar",
    "@duckutil//:duckutil_lib",
    "@duckutil//:lobstack_lib",
    "@duckutil//:duckutil_jsonrpc_lib",
    "@jsonrpc2_base//jar",
    "@jsonrpc2_server//jar",
    "@json_smart//jar",
    "@com_google_protobuf//:protobuf_java_util",
    "@build_stack_rules_proto//java:grpc_netty",
    ],
)

java_test(
    name = "lib.test",
    test_class = "lib.test.AllTests",
    srcs = glob(["test/**/*.java", "test/*.java"]),
    deps = [
        "//lib:lib",
        "//protolib:protogrpc",
        "@bcprov//jar",
        "@scprov//jar",
        "@commons_codec//jar",
        "@duckutil//:duckutil_lib"
    ]
)
