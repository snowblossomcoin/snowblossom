package client.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
  WalletTest.class,
  PurseTest.class,
  SeedTest.class,
  TransactionFactoryTest.class
})
public class AllTests
{}
