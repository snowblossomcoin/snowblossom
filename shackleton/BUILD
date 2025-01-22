package(default_visibility = ["//visibility:public"])

java_library(
  name = "shackleton",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
      "//protolib:protogrpc",
      "//lib:lib",
      "//client:client",
      "@duckutil//:duckutil_lib",
      "@duckutil//:webserver_lib",
      "@maven//:net_minidev_json_smart",
    ],
)

java_test(
    name = "shackleton.test",
    test_class = "shackleton.test.AllTests",
    srcs = glob(["test/**/*.java", "test/*.java"]),
    deps = [
		  "//shackleton:shackleton",
      "//lib:lib",
      "//protolib:protogrpc",
    ]
)

filegroup(
  name = "webstatic",
  srcs = glob(["webstatic/**"]),
)

