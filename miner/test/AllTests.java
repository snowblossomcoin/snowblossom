package miner.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@SuiteClasses({
  ShareManagerTest.class,
  SnowMerkleProofTest.class,
  FaQueueTest.class
})
public class AllTests
{}
