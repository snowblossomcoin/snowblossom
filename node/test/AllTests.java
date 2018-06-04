package node.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
  MemPoolTest.class,
  BlockIngestorTest.class,
})
public class AllTests
{}
