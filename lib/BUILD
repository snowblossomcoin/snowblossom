package(default_visibility = ["//visibility:public"])

java_library(
  name = "lib",
  srcs = glob(["src/*.java", "src/tls/*.java", "src/trie/*.java", "src/db/*.java", "src/db/lobstack/*.java"]),
  deps = [
    "//protolib:protogrpc",
    "@maven//:com_google_protobuf_protobuf_java",
    "@maven//:org_apache_commons_commons_math3",
    "@maven//:commons_codec_commons_codec",
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

java_library(
  name = "rocksdb",
  srcs = glob(["src/db/rocksdb/*.java"]),
  deps = [
    ":lib",
    "//protolib:protogrpc",
    "@maven//:org_rocksdb_rocksdbjni",
    "@duckutil//:duckutil_lib",
    "@maven//:junit_junit"
    ],
)

java_library(
  name = "atomicfiledb",
  srcs = glob(["src/db/atomicfile/*.java"]),
  deps = [
    ":lib",
    "//protolib:protogrpc",
    "@duckutil//:duckutil_lib",
    "@maven//:junit_junit"
    ],
)


java_test(
    name = "lib.test.trie.TrieTest",
    test_class = "lib.test.trie.TrieTest",
    srcs = glob(["test/trie/TrieTest.java"]),
    deps = [
        "//lib:lib",
        "//lib:rocksdb",
        "//protolib:protogrpc",
        "@maven//:commons_codec_commons_codec",
        "@maven//:org_bouncycastle_bcprov_jdk15on",
    ]
)


java_test(
    name = "lib.test.trie.TrieRocksTest",
    test_class = "lib.test.trie.TrieRocksTest",
    srcs = glob(["test/trie/TrieRocksTest.java"]),
    deps = [
        "//lib:lib",
        "//lib:rocksdb",
        "//protolib:protogrpc",
        "@maven//:commons_codec_commons_codec",
        "@maven//:org_bouncycastle_bcprov_jdk15on",
    ]
)


java_test(
    name = "lib.test.AddressUtilTest",
    test_class = "lib.test.AddressUtilTest",
    srcs = glob(["test/AddressUtilTest.java"]),
    deps = [
        "//lib:lib",
        "//lib:rocksdb",
        "//protolib:protogrpc",
        "@maven//:commons_codec_commons_codec",
        "@maven//:org_bouncycastle_bcprov_jdk15on",
    ]
)


java_test(
    name = "lib.test.BlockchainUtilTest",
    test_class = "lib.test.BlockchainUtilTest",
    srcs = glob(["test/BlockchainUtilTest.java"]),
    deps = [
        "//lib:lib",
        "//lib:rocksdb",
        "//protolib:protogrpc",
        "@maven//:commons_codec_commons_codec",
        "@maven//:org_bouncycastle_bcprov_jdk15on",
    ]
)


java_test(
    name = "lib.test.CipherUtilTest",
    test_class = "lib.test.CipherUtilTest",
    srcs = glob(["test/CipherUtilTest.java"]),
    deps = [
        "//lib:lib",
        "//lib:rocksdb",
        "//protolib:protogrpc",
        "@maven//:commons_codec_commons_codec",
        "@maven//:org_bouncycastle_bcprov_jdk15on",
    ]
)


java_test(
    name = "lib.test.DigestUtilTest",
    test_class = "lib.test.DigestUtilTest",
    srcs = glob(["test/DigestUtilTest.java"]),
    deps = [
        "//lib:lib",
        "//lib:rocksdb",
        "//protolib:protogrpc",
        "@maven//:commons_codec_commons_codec",
        "@maven//:org_bouncycastle_bcprov_jdk15on",
    ]
)


java_test(
    name = "lib.test.KeyUtilTest",
    test_class = "lib.test.KeyUtilTest",
    srcs = glob(["test/KeyUtilTest.java"]),
    deps = [
        "//lib:lib",
        "//lib:rocksdb",
        "//protolib:protogrpc",
        "@maven//:commons_codec_commons_codec",
        "@maven//:org_bouncycastle_bcprov_jdk15on",
    ]
)


java_test(
    name = "lib.test.PowUtilTest",
    test_class = "lib.test.PowUtilTest",
    srcs = glob(["test/PowUtilTest.java"]),
    deps = [
        "//lib:lib",
        "//lib:rocksdb",
        "//protolib:protogrpc",
        "@maven//:commons_codec_commons_codec",
        "@maven//:org_bouncycastle_bcprov_jdk15on",
    ]
)

java_test(
    name = "lib.test.ShardUtilTest",
    test_class = "lib.test.ShardUtilTest",
    srcs = glob(["test/ShardUtilTest.java"]),
    deps = [
        "//lib:lib",
        "//lib:rocksdb",
        "//protolib:protogrpc",
        "@maven//:commons_codec_commons_codec",
        "@maven//:org_bouncycastle_bcprov_jdk15on",
    ]
)


java_test(
    name = "lib.test.PRNGStreamTest",
    test_class = "lib.test.PRNGStreamTest",
    srcs = glob(["test/PRNGStreamTest.java"]),
    deps = [
        "//lib:lib",
        "//lib:rocksdb",
        "//protolib:protogrpc",
        "@maven//:commons_codec_commons_codec",
        "@maven//:org_bouncycastle_bcprov_jdk15on",
    ]
)


java_test(
    name = "lib.test.SignatureTest",
    test_class = "lib.test.SignatureTest",
    srcs = glob(["test/SignatureTest.java"]),
    deps = [
        "//lib:lib",
        "//lib:rocksdb",
        "//protolib:protogrpc",
        "@maven//:commons_codec_commons_codec",
        "@maven//:org_bouncycastle_bcprov_jdk15on",
    ]
)


java_test(
    name = "lib.test.SnowFallMerkleTest",
    test_class = "lib.test.SnowFallMerkleTest",
    srcs = glob(["test/SnowFallMerkleTest.java"]),
    deps = [
        "//lib:lib",
        "//lib:rocksdb",
        "//protolib:protogrpc",
        "@maven//:commons_codec_commons_codec",
        "@maven//:org_bouncycastle_bcprov_jdk15on",
    ]
)


java_test(
    name = "lib.test.ValidationTest",
    test_class = "lib.test.ValidationTest",
    srcs = glob(["test/ValidationTest.java"]),
    deps = [
        "//lib:lib",
        "//lib:rocksdb",
        "//protolib:protogrpc",
        "@maven//:commons_codec_commons_codec",
        "@maven//:org_bouncycastle_bcprov_jdk15on",
    ]
)


