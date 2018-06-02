package(default_visibility = ["//visibility:public"])

java_library(
  name = "snowblossomlib",
  srcs = glob(["**/*.java", "*.java"]),
  deps = [
    "//snowblossomprotolib:snowblossomprotolib",
    "@commons_codec//jar",
    "@commons_math3//jar",
    "@bcprov//jar",
    "@junit_junit//jar",
    "@org_rocksdb_rocksdbjni//jar",
    "@duckutil//:duckutil_lib",
    "@duckutil//:lobstack_lib",
    ],
)