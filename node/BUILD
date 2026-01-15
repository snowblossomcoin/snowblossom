package(default_visibility = ["//visibility:public"])

java_library(
  name = "node",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
      "//protolib:protogrpc",
      "//protolib:snow_java_proto",
      "@duckutil//:duckutil_lib",
      "@duckutil//:lobstack_lib",
      "//lib:lib",
      "//lib:rocksdb",
      "//lib:atomicfiledb",
      "//client:client",
      "@maven//:com_google_protobuf_protobuf_java",
      "@maven//:com_google_guava_guava",
      "@maven//:org_bitlet_weupnp",
      "@maven//:junit_junit",
      "@maven//:io_grpc_grpc_netty",
      "@maven//:io_grpc_grpc_api",
      "@maven//:io_grpc_grpc_stub",
      "@maven//:io_netty_netty_handler",
    ],
)


java_test(
    name = "node.test.MemPoolTest",
    test_class = "node.test.MemPoolTest",
    srcs = glob(["test/MemPoolTest.java"]),
    deps = [
        "//node:node",
        "//lib:lib",
        "//protolib:protogrpc",
        "//protolib:snow_java_proto",
        "@duckutil//:duckutil_lib",
        "@maven//:com_google_guava_guava",
    ]
)

java_test(
    name = "node.test.BlockIngestorTest",
    test_class = "node.test.BlockIngestorTest",
    srcs = glob(["test/BlockIngestorTest.java"]),
    deps = [
        "//node:node",
        "//lib:lib",
        "//protolib:protogrpc",
        "//protolib:snow_java_proto",
        "@duckutil//:duckutil_lib",
    ]
)

java_test(
    name = "node.test.BlockForgeTest",
    test_class = "node.test.BlockForgeTest",
    srcs = glob(["test/BlockForgeTest.java"]),
    deps = [
        "//node:node",
        "//lib:lib",
        "//protolib:protogrpc",
        "//protolib:snow_java_proto",
        "@maven//:com_google_protobuf_protobuf_java",
        "@duckutil//:duckutil_lib",
    ]
)

java_test(
    name = "node.test.ForBenefitOfUtilTest",
    test_class = "node.test.ForBenefitOfUtilTest",
    srcs = glob(["test/ForBenefitOfUtilTest.java"]),
    deps = [
        "//node:node",
        "//lib:lib",
        "//protolib:protogrpc",
        "@duckutil//:duckutil_lib",
    ]
)

