package lib.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import lib.test.trie.TrieRocksTest;
import lib.test.trie.TrieTest;

@RunWith(Suite.class)
@SuiteClasses({
  // /trie
  TrieTest.class,
  TrieRocksTest.class,

  // root
  AddressUtilTest.class,
  BlockchainUtilTest.class,
  DigestUtilTest.class,
  KeyUtilTest.class,
  PowUtilTest.class,
  PRNGStreamTest.class,
  SignatureTest.class,
  SnowFallMerkleTest.class,
  ValidationTest.class
})
public class AllTests {}
