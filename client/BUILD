package(default_visibility = ["//visibility:public"])

java_library(
  name = "client",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
    "//protolib:protogrpc",
    "//lib:lib",
    "@duckutil//:duckutil_lib",
    "@duckutil//:lobstack_lib",
    "@duckutil//:duckutil_jsonrpc_lib",
    "@com_google_protobuf//:protobuf_java_util",
    "@io_grpc_grpc_java//netty",
    "@io_netty_netty_handler//:io_netty_netty_handler",
    "@maven//:com_thetransactioncompany_jsonrpc2_base",
    "@maven//:com_thetransactioncompany_jsonrpc2_server",
    "@maven//:net_minidev_json_smart",
    "@maven//:org_bitcoinj_bitcoinj_core",
    "@maven//:com_lambdaworks_scrypt",
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
        "@duckutil//:duckutil_lib",
        "@maven//:org_bitcoinj_bitcoinj_core",
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
        "@duckutil//:duckutil_lib",
        "@maven//:org_bitcoinj_bitcoinj_core",
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
    ]
)
