package(default_visibility = ["//visibility:public"])

java_library(
  name = "lib",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
    "//protolib:protogrpc",
    "@maven//:com_google_protobuf_protobuf_java",
    "@maven//:org_apache_commons_commons_math3",
    "@maven//:commons_codec_commons_codec",
    "@maven//:org_rocksdb_rocksdbjni",
    "@maven//:org_bouncycastle_bcprov_jdk15on",
    "@maven//:org_bouncycastle_bcpkix_jdk15on",
    "@maven//:com_thetransactioncompany_jsonrpc2_server",
    "@maven//:net_minidev_json_smart",
    "@maven//:com_thetransactioncompany_jsonrpc2_base",
    "@maven//:junit_junit",
    "@maven//:io_netty_netty_tcnative_boringssl_static",
    "@com_google_protobuf//:protobuf_java_util",
    "@io_netty_netty_handler//:io_netty_netty_handler",
    "@duckutil//:duckutil_lib",
    "@duckutil//:lobstack_lib",
    "@duckutil//:duckutil_jsonrpc_lib",
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
        "@maven//:commons_codec_commons_codec",
        "@maven//:org_bouncycastle_bcprov_jdk15on",
    ]
)
