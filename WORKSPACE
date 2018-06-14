git_repository(
  name = "duckutil",
  remote = "https://github.com/fireduck64/duckutil",
  tag = "1.8",
)

maven_jar(
  name = "protobuf",
  artifact = "com.google.protobuf:protobuf-java:3.5.1",
  sha1 = "8c3492f7662fa1cbf8ca76a0f5eb1146f7725acd",
)


git_repository(
  name = "org_pubref_rules_protobuf",
  remote = "https://github.com/fireduck64/rules_protobuf",
  tag = "gnet-up3",
)

load("@org_pubref_rules_protobuf//java:rules.bzl", "java_proto_repositories")
java_proto_repositories()

maven_jar(
  name = "org_rocksdb_rocksdbjni",
  artifact = "org.rocksdb:rocksdbjni:5.11.3",
  sha1 = "a177b51aa2797794757fcaf4178044dbfe1b5834",
)

maven_jar(
  name = "junit_junit",
  artifact = "junit:junit:4.12",
  sha1 = "2973d150c0dc1fefe998f834810d68f278ea58ec",
)

maven_jar(
  name = "commons_codec",
  artifact = "commons-codec:commons-codec:1.11",
  sha1 = "3acb4705652e16236558f0f4f2192cc33c3bd189",
)

maven_jar(
  name = "commons_io",
  artifact = "commons-io:commons-io:2.6",
  sha1 = "815893df5f31da2ece4040fe0a12fd44b577afaf",
)
maven_jar(
  name = "commons_math3",
  artifact = "org.apache.commons:commons-math3:3.6.1",
  sha1 = "e4ba98f1d4b3c80ec46392f25e094a6a2e58fcbf",
)

maven_jar(
  name = "bcprov",
  artifact = "org.bouncycastle:bcprov-jdk15on:1.59",
  sha1 = "2507204241ab450456bdb8e8c0a8f986e418bd99",
)

