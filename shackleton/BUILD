package(default_visibility = ["//visibility:public"])

java_library(
  name = "shackleton",
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
    ],
)

java_test(
    name = "shackleton.test",
    test_class = "shackleton.test.AllTests",
    srcs = glob(["test/**/*.java", "test/*.java"]),
    deps = [
		  "//shackleton:shackleton",
      "//lib:lib",
      "//protolib:protolib",
    ]
)