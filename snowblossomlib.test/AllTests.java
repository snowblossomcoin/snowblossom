package snowblossomlib.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import snowblossomlib.test.trie.TrieRocksTest;
import snowblossomlib.test.trie.TrieTest;

@RunWith(Suite.class)
@SuiteClasses({
  TrieRocksTest.class,
  TrieTest.class
})
public class AllTests {}