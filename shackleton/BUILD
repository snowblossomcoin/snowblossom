package(default_visibility = ["//visibility:public"])

java_library(
  name = "shackleton",
  srcs = glob(["src/**/*.java", "src/*.java"]),
  deps = [
      "//protolib:protogrpc",
      "//protolib:snow_java_proto",
      "//lib:lib",
      "//client:client",
      "@duckutil//:duckutil_lib",
      "@duckutil//:webserver_lib",
      "@maven//:com_google_protobuf_protobuf_java",
      "@maven//:com_google_protobuf_protobuf_java_util",
      "@maven//:net_minidev_json_smart",
      "@maven//:io_grpc_grpc_api",
      "@maven//:io_grpc_grpc_stub",
      "@maven//:com_google_guava_guava",
    ],
)

java_test(
    name = "shackleton.test",
    test_class = "shackleton.test.AllTests",
    srcs = glob(["test/**/*.java", "test/*.java"]),
    deps = [
		  "//shackleton:shackleton",
      "//lib:lib",
    ]
)

filegroup(
  name = "webstatic",
  srcs = glob(["webstatic/**"]),
)

