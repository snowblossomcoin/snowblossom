
java_binary(
  name = "SnowBlossomNode",
  main_class = "snowblossom.node.SnowBlossomNode",
  jvm_flags = [ 
    "-Xms4g -Xmx4g",
  ],
  runtime_deps = [
    "//node",
  ]
)

java_binary(
  name = "SnowBlossomMiner",
  main_class = "snowblossom.miner.SnowBlossomMiner",
  runtime_deps = [
    "//miner:miner",
  ]
)

java_binary(
  name = "PoolMiner",
  main_class = "snowblossom.miner.PoolMiner",
  runtime_deps = [
    "//miner:miner",
  ]
)

java_binary(
  name = "Arktika",
  main_class = "snowblossom.miner.Arktika",
  runtime_deps = [
    "//miner:miner",
  ]
)

java_binary(
  name = "SurfMiner",
  main_class = "snowblossom.miner.surf.SurfMiner",
  runtime_deps = [
    "//miner:miner",
  ]
)

java_binary(
  name = "MrPlow",
  main_class = "snowblossom.miner.plow.MrPlow",
  jvm_flags = [ 
    "-Xms4g -Xmx4g",
  ],
  runtime_deps = [
    "//miner:miner",
  ]
)

java_binary(
  name = "MrPlowDataMigrate",
  main_class = "snowblossom.miner.plow.DataMigrate",
  runtime_deps = [
    "//miner:miner",
  ]
)


java_binary(
  name = "ShackletonExplorer",
  main_class = "snowblossom.shackleton.Shackleton",
  resources = [ "//shackleton:webstatic" ],
  jvm_flags = [ 
    "-Xms1g -Xmx1g",
  ],
  runtime_deps = [
    "//shackleton:shackleton",
  ]
)

java_binary(
  name = "RichList",
  main_class = "snowblossom.shackleton.RichList",
  runtime_deps = [
    "//shackleton:shackleton",
  ]
)

java_binary(
  name = "MinerReport",
  main_class = "snowblossom.shackleton.MinerReport",
  runtime_deps = [
    "//shackleton:shackleton",
  ]
)


java_binary(
  name = "SnowBlossomClient",
  main_class = "snowblossom.client.SnowBlossomClient",
  runtime_deps = [
    "//client:client",
  ]
)

java_binary(
  name = "IceLeaf",
  main_class = "snowblossom.iceleaf.IceLeaf",
  resources = [ "//iceleaf-ui:resources" ], 
  runtime_deps = [
    "//iceleaf-ui:iceleaf",
  ]
)

java_binary(
  name = "IceLeafTestnet",
  main_class = "snowblossom.iceleaf.IceLeafTestnet",
  resources = [ "//iceleaf-ui:resources" ], 
  runtime_deps = [
    "//iceleaf-ui:iceleaf",
  ]
)

java_binary(
  name = "Everything",
  main_class = "snowblossom.iceleaf.IceLeaf",
  resources = [ "//iceleaf-ui:resources", "//shackleton:webstatic" ],
  runtime_deps = [
    "//lib:lib",
    "//node:node",
    "//miner:miner",
    "//client:client",
    "//shackleton:shackleton",
    "//iceleaf-ui:iceleaf",
  ]
)


java_binary(
  name = "VanityGen",
  main_class = "snowblossom.client.VanityGen",
  runtime_deps = [
    "//client:client",
  ]
)

java_binary(
  name = "SnowFall",
  main_class = "snowblossom.lib.SnowFall",
  runtime_deps = [
    "//lib:lib",
  ]
)

java_binary(
  name = "SnowMerkle",
  main_class = "snowblossom.lib.SnowMerkle",
  runtime_deps = [
    "//lib:lib",
  ]
)

java_binary(
  name = "ShowAlgo",
  main_class = "snowblossom.lib.ShowAlgo",
  runtime_deps = [
    "//lib:lib",
  ]
)
