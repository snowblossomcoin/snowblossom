package(default_visibility = ["//visibility:public"])

java_library(
  name = "client",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
    "//protolib:protogrpc",
    "//protolib:snow_java_proto",
    "//lib:lib",
    "@maven//:com_google_protobuf_protobuf_java",
    "@maven//:com_google_protobuf_protobuf_java_util",
    "@duckutil//:duckutil_lib",
    "@duckutil//:lobstack_lib",
    "@duckutil//:duckutil_jsonrpc_lib",
    "@maven//:com_google_guava_guava",
    "@maven//:com_thetransactioncompany_jsonrpc2_base",
    "@maven//:com_thetransactioncompany_jsonrpc2_server",
    "@maven//:net_minidev_json_smart",
    "@maven//:org_bitcoinj_bitcoinj_core",
    "@maven//:com_lambdaworks_scrypt",
    "@maven//:io_grpc_grpc_netty",
    "@maven//:io_grpc_grpc_api",
    "@maven//:io_grpc_grpc_stub",
    "@maven//:io_netty_netty_handler",
    ],
)

java_test(
    name = "client.test.WalletTest",
    test_class = "client.test.WalletTest",
    srcs = glob(["test/WalletTest.java"]),
    deps = [
        "//client:client",
        "//lib:lib",
        "//protolib:protogrpc",
        "//protolib:snow_java_proto",
        "@duckutil//:duckutil_lib",
        "@maven//:com_google_guava_guava",
        "@maven//:org_bitcoinj_bitcoinj_core",
        "@maven//:com_google_protobuf_protobuf_java",
    ]
)

java_test(
    name = "client.test.PurseTest",
    test_class = "client.test.PurseTest",
    srcs = glob(["test/PurseTest.java"]),
    deps = [
        "//client:client",
        "//lib:lib",
        "//protolib:protogrpc",
        "//protolib:snow_java_proto",
        "@duckutil//:duckutil_lib",
        "@maven//:org_bitcoinj_bitcoinj_core",
    ]
)

java_test(
    name = "client.test.SeedTest",
    test_class = "client.test.SeedTest",
    srcs = glob(["test/SeedTest.java"]),
    deps = [
        "//client:client",
        "//lib:lib",
        "//protolib:protogrpc",
        "//protolib:snow_java_proto",
        "@duckutil//:duckutil_lib",
        "@maven//:org_bitcoinj_bitcoinj_core",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_guava_guava",
    ]
)

java_test(
    name = "client.test.TransactionFactoryTest",
    test_class = "client.test.TransactionFactoryTest",
    srcs = glob(["test/TransactionFactoryTest.java"]),
    deps = [
        "//client:client",
        "//lib:lib",
        "//protolib:protogrpc",
        "//protolib:snow_java_proto",
        "@maven//:com_google_guava_guava",
        "@duckutil//:duckutil_lib",
        "@maven//:org_bitcoinj_bitcoinj_core",
    ]
)

java_test(
    name = "client.test.PasswordCryptTest",
    test_class = "client.test.PasswordCryptTest",
    srcs = glob(["test/PasswordCryptTest.java"]),
    deps = [
      "//client:client",
      "//lib:lib",
      "//protolib:protogrpc",
      "@duckutil//:duckutil_lib",
      "@maven//:org_bitcoinj_bitcoinj_core",
      "@maven//:com_google_protobuf_protobuf_java",
    ]
)
