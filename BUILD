

java_binary(
  name = "SnowBlossomNode",
  main_class = "snowblossom.SnowBlossomNode",
  jvm_flags = [ 
    "-Xmx1600M",
  ],
  runtime_deps = [
    "//lib:lib",
  ]
)
java_binary(
  name = "SnowBlossomMiner",
  main_class = "snowblossom.miner.SnowBlossomMiner",
  runtime_deps = [
    ":minerlib",
  ]
)
java_binary(
  name = "Shackleton",
  main_class = "snowblossom.shackleton.Shackleton",
  runtime_deps = [
    ":shackletonlib",
  ]
)

java_binary(
  name = "SnowBlossomClient",
  main_class = "snowblossom.client.SnowBlossomClient",
  runtime_deps = [
    ":clientlib",
  ]
)


java_binary(
  name = "SnowFall",
  main_class = "snowblossom.SnowFall",
  runtime_deps = [
    "//lib:lib",
  ]
)
java_binary(
  name = "SnowMerkle",
  main_class = "snowblossom.SnowMerkle",
  runtime_deps = [
    "//lib:lib",
  ]
)

java_binary(
  name = "ShowAlgo",
  main_class = "snowblossom.ShowAlgo",
  runtime_deps = [
    "//lib:lib",
  ]
)

java_library(
  name = "minerlib",
  srcs = glob(["src/miner/*.java"]),
  deps = [
    "//protolib:protolib",
    "//lib:lib",
    "@junit_junit//jar",
    "@commons_codec//jar",
    "@bcprov//jar",
    "@duckutil//:duckutil_lib",
  ],
)

java_library(
  name = "shackletonlib",
  srcs = glob(["src/shackleton/*.java"]),
  deps = [
    "//protolib:protolib",
    "//lib:lib",
    "@junit_junit//jar",
    "@commons_codec//jar",
    "@bcprov//jar",
    "@duckutil//:duckutil_lib",
  ],
)


java_library(
  name = "clientlib",
  srcs = glob(["src/client/*.java"]),
  deps = [
    "//protolib:protolib",
    "//lib:lib",
    "@junit_junit//jar",
    "@commons_codec//jar",
    "@bcprov//jar",
    "@duckutil//:duckutil_lib",
  ],
)

java_test(
  name = "prng_stream_test",
  srcs = ["test/PRNGStreamTest.java"],
  test_class = "snowblossom.PRNGStreamTest",
  size="small",
  deps = [
      "@junit_junit//jar",
      "//lib:lib",
      "@org_pubref_rules_protobuf//java:grpc_compiletime_deps",
      "@commons_codec//jar",
  ],
)

java_test(
  name = "block_ingestor_test",
  srcs = ["test/BlockIngestorTest.java"],
  test_class = "snowblossom.BlockIngestorTest",
  size="small",
  deps = [
      "@junit_junit//jar",
      "//lib:lib",
      "@commons_codec//jar",
      "//protolib:protolib",
  ],
)

java_test(
  name = "pow_util_test",
  srcs = ["test/PowUtilTest.java"],
  test_class = "snowblossom.PowUtilTest",
  size="small",
  deps = [
      "@junit_junit//jar",
      "//lib:lib",
      "@commons_codec//jar",
      "//protolib:protolib",
  ],
)
java_test(
  name = "blockchain_util_test",
  srcs = ["test/BlockchainUtilTest.java"],
  test_class = "snowblossom.BlockchainUtilTest",
  size="small",
  deps = [
      "@junit_junit//jar",
      "//lib:lib",
      "@commons_codec//jar",
      "@org_pubref_rules_protobuf//java:grpc_compiletime_deps",
  ],
)

java_test(
  name = "address_util_test",
  srcs = ["test/AddressUtilTest.java"],
  test_class = "snowblossom.AddressUtilTest",
  size="small",
  deps = [
      "@junit_junit//jar",
      "//lib:lib",
      "@commons_codec//jar",
      "//protolib:protolib",
  ],
)
java_test(
  name = "digest_util_test",
  srcs = ["test/DigestUtilTest.java"],
  test_class = "snowblossom.DigestUtilTest",
  size="small",
  deps = [
      "@junit_junit//jar",
      "//lib:lib",
      "@commons_codec//jar",
      "//protolib:protolib",
  ],
)

java_test(
  name = "signature_test",
  srcs = ["test/SignatureTest.java"],
  test_class = "snowblossom.SignatureTest",
  size="medium",
  deps = [
      "@junit_junit//jar",
      "//lib:lib",
      "@commons_codec//jar",
      "@bcprov//jar",
  ],
)

java_test(
  name = "wallet_test",
  srcs = ["test/WalletTest.java"],
  test_class = "snowblossom.WalletTest",
  size="large",
  deps = [
      "@junit_junit//jar",
      "//lib:lib",
      "//protolib:protolib",
      "@commons_codec//jar",
      "@bcprov//jar",
  ],
)


java_test(
  name = "validation_test",
  srcs = ["test/ValidationTest.java"],
  test_class = "snowblossom.ValidationTest",
  size="small",
  deps = [
      "@junit_junit//jar",
      "//lib:lib",
      "@commons_codec//jar",
      "@org_pubref_rules_protobuf//java:grpc_compiletime_deps",
      "//protolib:protolib",
  ],
)
java_test(
  name = "mem_pool_test",
  srcs = ["test/MemPoolTest.java"],
  test_class = "snowblossom.MemPoolTest",
  size="medium",
  deps = [
      "@junit_junit//jar",
      "//lib:lib",
      "@commons_codec//jar",
      "@org_pubref_rules_protobuf//java:grpc_compiletime_deps",
      "@duckutil//:duckutil_lib",
      "//protolib:protolib",
  ],
)
java_test(
  name = "keyutil_test",
  srcs = ["test/KeyUtilTest.java"],
  test_class = "snowblossom.KeyUtilTest",
  size="medium",
  deps = [
      "@junit_junit//jar",
      "//lib:lib",
      "@commons_codec//jar",
      "@org_pubref_rules_protobuf//java:grpc_compiletime_deps",
      "//protolib:protolib",
  ],
)


java_test(
  name = "spoon_test",
  srcs = ["test/SpoonTest.java"],
  test_class = "snowblossom.SpoonTest",
  size="medium",
  deps = [
      "@junit_junit//jar",
      "//lib:lib",
      "@commons_codec//jar",
      "@org_pubref_rules_protobuf//java:grpc_compiletime_deps",
      "@duckutil//:duckutil_lib",
      "//protolib:protolib",
      ":minerlib",
      ":clientlib",
  ],
)






java_test(
  name = "snow_fall_merkle_test",
  srcs = ["test/SnowFallMerkleTest.java"],
  test_class = "snowblossom.SnowFallMerkleTest",
  size="medium",
  deps = [
      "@junit_junit//jar",
      "//lib:lib",
      "@org_pubref_rules_protobuf//java:grpc_compiletime_deps",
      "@commons_codec//jar",
  ],
)

java_test(
  name = "snow_merkle_proof_test",
  srcs = ["test/SnowMerkleProofTest.java"],
  test_class = "snowblossom.SnowMerkleProofTest",
  size="medium",
  deps = [
      "@junit_junit//jar",
      "//lib:lib",
      "//protolib:protolib",
      "@org_pubref_rules_protobuf//java:grpc_compiletime_deps",
      "@commons_codec//jar",
  ],
)




