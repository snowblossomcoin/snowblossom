package node.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@SuiteClasses({
  MemPoolTest.class,
  BlockIngestorTest.class,
  BlockForgeTest.class
})
public class AllTests
{}
