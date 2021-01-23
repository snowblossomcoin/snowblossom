package lib.test;

import lib.test.trie.TrieRocksTest;
import lib.test.trie.TrieTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite.SuiteClasses;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@SuiteClasses({
  // /trie
  TrieTest.class,
  TrieRocksTest.class,

  // root
  AddressUtilTest.class,
  BlockchainUtilTest.class,
  CipherUtilTest.class,
  DigestUtilTest.class,
  KeyUtilTest.class,
  PowUtilTest.class,
  PRNGStreamTest.class,
  SignatureTest.class,
  SnowFallMerkleTest.class,
  ValidationTest.class,
  ShardUtilTest.class
})
public class AllTests {}
