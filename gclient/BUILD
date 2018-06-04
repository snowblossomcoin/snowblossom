package(default_visibility = ["//visibility:public"])

java_library(
  name = "gclient",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
    "//protolib:protolib",
    "//lib:lib",
    "//wallet:wallet",
    "@commons_codec//jar",
    "@commons_math3//jar",
    "@bcprov//jar",
    "@junit_junit//jar",
    "@org_rocksdb_rocksdbjni//jar",
    "@duckutil//:duckutil_lib",
    "@duckutil//:lobstack_lib",
    ],
)

java_test(
    name = "gclient.test",
    test_class = "client.test.AllTests",
    srcs = glob(["test/**/*.java", "test/*.java"]),
    deps = [
		    "//gclient:gclient",
        "//lib:lib",
        "//protolib:protolib",
        "//wallet:wallet"
    ]
)