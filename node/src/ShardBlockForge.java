package snowblossom.node;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import duckutil.Pair;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.io.FileInputStream;
import java.util.Scanner;
import snowblossom.lib.*;
import snowblossom.lib.trie.HashUtils;
import snowblossom.proto.*;
import duckutil.PeriodicThread;
import duckutil.LRUCache;

public class ShardBlockForge
{

  public static final int IMPORT_PASSES=4;

  private SnowBlossomNode node;
  private NetworkParams params;
  private static final Logger logger = Logger.getLogger("snowblossom.node");
 
  private final PrintStream forge_log;
  private TimeRecord time_record;

  private volatile long last_template_request = 0L;
  private volatile ArrayList<BlockConcept> current_top_concepts = null;
  private ConceptUpdateThread concept_update_thread;

  public ShardBlockForge(SnowBlossomNode node)
    throws Exception
  {
    this.node = node;
    this.params = node.getParams();
    if (node.getConfig().isSet("forge_log"))
    {
      forge_log = new PrintStream(new FileOutputStream(node.getConfig().get("forge_log"), true));
      time_record = new TimeRecord();
      TimeRecord.setSharedRecord(time_record);
    }
    else
    {
      forge_log = null;
    }

    concept_update_thread = new ConceptUpdateThread();
    concept_update_thread.start();

    if (node.getConfig().isSet("forge_col_check"))
    {
      testingCollisionCheck();
    }

  }

  public void testingCollisionCheck()
    throws Exception
  {
    Scanner scan = new Scanner(new FileInputStream(node.getConfig().get("forge_col_check")));

    Map<String, ChainHash> known_map = new HashMap<>();

    while(scan.hasNext())
    {
      String h = scan.next().replace(",","").trim();
      ChainHash hash = new ChainHash(h);
      BlockSummary bs = getDBSummary(hash);

      known_map = checkCollisions(known_map, bs);
      if (known_map==null)
      {
        System.out.println(String.format("Check for %s failed", hash.toString()));
        return;
      }
      else
      {
        System.out.println(String.format("Check for %s ok.  Entries: %d", hash.toString(), known_map.size()));
      }

    }


  }

  public Block getBlockTemplate(SubscribeBlockTemplateRequest mine_to)
  {
    try(TimeRecordAuto tra_gbt = TimeRecord.openAuto("ShardBlockForge.getBlockTemplate"))
    {
      last_template_request = System.currentTimeMillis();

      if (node.getBlockIngestor(0).getHead() == null)
      {
        // Let the old thing do the genesis gag
        return node.getBlockForge(0).getBlockTemplate(mine_to);
      }

      ArrayList<BlockConcept> possible_set = current_top_concepts;

      if (possible_set == null)
      {
        concept_update_thread.wakeAndWait();
        possible_set = current_top_concepts;
      }
      if (possible_set == null) return null;
      if (possible_set.size() == 0) return null; 

      Random rnd = new Random();

      // Random
      //BlockConcept selected = possible_set.get( rnd.nextInt(possible_set.size()) );

      // First 4
      BlockConcept selected = possible_set.get( rnd.nextInt(Math.min(4,possible_set.size())) );
      try
      {
        return fleshOut(selected, mine_to); 

      }
      catch(ValidationException e)
      {
        logger.warning("Validation failed in block fleshOut: " + e);
        return null;
      }
    }
    catch(Throwable t)
    {
      logger.warning("Exception in getBlockTemplate: " + t);
      t.printStackTrace();
      return null;
    }
  }

  /**
   * Return a set of block concepts
   * @param possible_source_blocks - blocks to use as parents of new blocks
   * @param require_better - require that any proposed blocks be better, either longer chain or higher worksum that existing
   * @param restrict_known_map - restrict shard imports to those that do not conflict with this map
   */
  private Set<BlockConcept> exploreBlocks(Set<ChainHash> possible_source_blocks, boolean require_better, Map<String, ChainHash> restrict_known_map)
  {
    // Concepts to explore
    LinkedList<BlockConcept> concept_list = new LinkedList<>();

    for(ChainHash hash : possible_source_blocks)
    {
      BlockSummary bs = getDBSummary(hash.getBytes());
      if (bs != null)
      if (node.getInterestShards().contains(bs.getHeader().getShardId()))
      {
        concept_list.addAll( initiateBlockConcepts(bs) );
      }
    }

    TreeSet<BlockConcept> possible_set = new TreeSet<>();

    for(BlockConcept bc : concept_list)
    {
      for(BlockConcept bc_with : importShards(bc, restrict_known_map))
      {
        if (testBlock(bc_with))
        {
          if ((!require_better) || isBetter(bc_with))
          {
            possible_set.add(bc_with);
          }
        }
      }
    }

    return possible_set;

  }

  private boolean checkCollisions(BlockConcept b)
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.checkCollisions(b)"))
    {
      HashMap<String, ChainHash> known_map = new HashMap<>();
      
      // Check summary map list
      Validation.checkCollisions(known_map, b.getPrevSummary().getShardHistoryMap());

      // Check all imported shard headers
      for(ImportedBlock ib : b.getImportedBlocks())
      {
        BlockHeader h = ib.getHeader();
        Validation.checkCollisions(known_map, h.getShardId(), h.getBlockHeight(), new ChainHash(h.getSnowHash()));
        Validation.checkCollisions(known_map, h.getShardImportMap());
      }

      Validation.checkCollisions(known_map, b.getPrevSummary().getShardHistoryMap());
      Validation.checkCollisions(known_map, b.getHeader().getShardImportMap());
      Validation.checkCollisions(known_map, b.getHeader().getShardId(), b.getHeader().getBlockHeight(), ChainHash.getRandom());
      return true;
    }
    catch(ValidationException e)
    {
      return false;
    }

  }

  private boolean testBlock(BlockConcept b)
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.testBlock"))
    {
      if (b==null) return false;
      if (b.getHeader().getVersion() == 1) return true;
      if (b.getHeader().getBlockHeight()==0) return true;

      try
      {
        BlockHeader bh = BlockHeader.newBuilder()
          .mergeFrom(b.getHeader())
          .setSnowHash(ChainHash.getRandom().getBytes())
          .build();
        Block test_block = Block.newBuilder()
          .setHeader(bh)
          .addAllImportedBlocks(b.getImportedBlocks())
          .build();

        Validation.checkShardBasics(test_block, b.getPrevSummary(), params);
        return true;
      }
      catch(ValidationException e)
      {
        // logger.info("Validation failed in testBlock: " + e);
        return false;
      }
    }
  }

  private boolean isBetter(BlockConcept b)
  {
    if (b.getAdvancesShard() > 0) return true;

    BlockHeader header = b.getHeader();

    if (b.getWorkSum().compareTo(
      node.getDB().getBestBlockAt(header.getShardId(), header.getBlockHeight())) > 0) return true;

    return false;

  }

  /**
   * Go down by depth and then return all blocks that are decendend from that one
   * and are on the same shard id.
   */
  public Set<ChainHash> getBlocksAround(ChainHash start, int depth, int shard_id)
  {

    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.getBlocksAround"))
    {
      ChainHash tree_root = descend(start, depth);

      return climb(tree_root, shard_id);
    }
  }


  public Map<String, ChainHash> getKnownMap(Set<ChainHash> hash_set)
  {
    Map<String, ChainHash> known_map = new HashMap<>(2048,0.5f);

    for(ChainHash ch : hash_set)
    {
      BlockSummary bs = getDBSummary(ch);
      known_map = checkCollisions(known_map, bs);
      if (known_map == null) throw new RuntimeException("getKnownMap on conflicting data set");

    }
    return known_map;

  }

  /**
   * Returns the highest value set of blocks that all work together that we can find
   */ 
  public Set<ChainHash> getGoldenSet(int depth)
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.getGoldenSet(" + depth + ")"))
    {
      // TODO find a better way to get the set of shards we need
     
      System.out.println("Finding golden set - depth " + depth);
      Map<Integer,Set<ChainHash> > head_shards = new TreeMap<Integer, Set<ChainHash> >();

      int max_height = 0;
      for(int s : node.getActiveShards())
      {
        if (node.getBlockIngestor(s).getHead()!=null)
        {
          max_height = Math.max( max_height, node.getBlockIngestor(s).getHead().getHeader().getBlockHeight() );
        }

      }


      for(int s : node.getActiveShards())
      {
        if (node.getBlockIngestor(s).getHead()!=null)
        {
          BlockHeader h = node.getBlockIngestor(s).getHead().getHeader();

          if (h.getBlockHeight() + params.getMaxShardSkewHeight()*2 >= max_height)
          { // anything super short is probably irrelvant noise - an old shard that is no longer extended

            head_shards.put(s, 
              getBlocksAround( new ChainHash(h.getSnowHash()), depth, s));
          }
        }
      }
      //System.out.println("Shard heads: " + head_shards);
      Map<Integer, BlockSummary> gold = getGoldenSetRecursive(head_shards, ImmutableMap.of(), false);

      if (gold == null)
      {
        System.out.println("No gold set - depth " + depth);
        return new HashSet<ChainHash>();
      }

      gold = goldUpgrade(gold);

      HashSet<ChainHash> gold_set = new HashSet<ChainHash>();

      for(BlockSummary bs : gold.values())
      {
        gold_set.add(new ChainHash(bs.getHeader().getSnowHash()));
      }

      System.out.println("Gold set found: " + gold_set);

      return gold_set;
    }
  }

  private Map<Integer, BlockSummary> goldUpgrade( Map<Integer, BlockSummary> gold_in)
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.goldUpgrade"))
    {
      Map<Integer,Set<ChainHash> > head_shards = new TreeMap<Integer, Set<ChainHash> >();
      for(int s : gold_in.keySet())
      {
        head_shards.put(s, climb( new ChainHash(gold_in.get(s).getHeader().getSnowHash()), s));
      }
      return getGoldenSetRecursive(head_shards, ImmutableMap.of(), false);
    }
  }

  /**
   * Find blocks for the remaining_shards that don't conflict with anything in known_map.
   * @returns the map of shards to headers that is the best
   */
  private Map<Integer, BlockSummary> getGoldenSetRecursive(Map<Integer, Set<ChainHash> > remaining_shards, ImmutableMap<String, ChainHash> known_map, boolean short_cut)
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.getGoldenSetRecursive"))
    {
      if (remaining_shards.size() == 0) return new TreeMap<Integer, BlockSummary>();

      TreeMap<Integer, Set<ChainHash> > rem_shards_tree = new TreeMap<>();
      rem_shards_tree.putAll(remaining_shards);

      int shard_id = rem_shards_tree.firstEntry().getKey();

      Set<ChainHash> shard_blocks = rem_shards_tree.pollFirstEntry().getValue();

      BigInteger best_solution_val = BigInteger.valueOf(-1L);
      Map<Integer, BlockSummary> best_solution = null;
      

      for(ChainHash hash : shard_blocks)
      {
        BlockSummary bs = getDBSummary(hash);
        Map<String, ChainHash> block_known_map = checkCollisions( known_map, bs);

        // If this block doesn't collide
        if (block_known_map != null)
        {
          // Add in blocker for self on next block to make sure no one has ideas for it
          if (Validation.checkCollisionsNT( block_known_map, shard_id, bs.getHeader().getBlockHeight()+1, ChainHash.ZERO_HASH))
          {

            Map<Integer, BlockSummary> sub_solution = getGoldenSetRecursive( rem_shards_tree, ImmutableMap.copyOf(block_known_map), short_cut);
            if (sub_solution != null)
            {
              sub_solution.put(shard_id, bs);
              BigInteger val_sum = BigInteger.ZERO;
              for(BlockSummary bs_sub : sub_solution.values())
              {
                val_sum  = val_sum.add( BlockchainUtil.readInteger( bs.getWorkSum() ));
              }
          
              if (val_sum.compareTo(best_solution_val) > 0)
              {
                best_solution = sub_solution;
                best_solution_val = val_sum;
                if (short_cut)
                {
                  return best_solution; // short circuit
                }
              }
            }
          }
        }
      }


      return best_solution;
    }
  }

  // TODO - we can also squeeze some more performance by stacking the maps rather than copying into these immutable maps all the time
  private Map<String, ChainHash> checkCollisions(Map<String, ChainHash> known_map_in, BlockSummary bs)
  {

    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.checkCollisions(m,bs)"))
    {
      HashMap<String, ChainHash> known_map = new HashMap<>(2048,0.5f);
      known_map.putAll( known_map_in );

      BlockHeader h = bs.getHeader();

        // TODO switch to non-exception throwing methods
        //  exceptions are expensive with the stack trace introspection and we throw a ton of them
        if (Validation.checkCollisionsNT(known_map, bs.getShardHistoryMap()))
        if (Validation.checkCollisionsNT(known_map, h.getShardId(), h.getBlockHeight(), new ChainHash(h.getSnowHash())))
        if (Validation.checkCollisionsNT(known_map, h.getShardImportMap()))
        return known_map;

        return null;
    }

  }

  public Set<ChainHash> climb(ChainHash start, int shard_id)
  {
    HashSet<ChainHash> set = new HashSet<>();
    BlockSummary bs = getDBSummary(start);
  
    if (bs != null)
    if (bs.getHeader().getShardId() == shard_id)
    {
      set.add(new ChainHash(bs.getHeader().getSnowHash()));
    }


    for(ByteString next : node.getDB().getChildBlockMapSet().getSet(start.getBytes(), 2000))
    {
      set.addAll(climb(new ChainHash(next), shard_id));
    }

    return set;
    
  }

  public ChainHash descend(ChainHash start, int depth)
  {
    if (depth==0) return start;

    BlockSummary bs = getDBSummary(start);
    if (bs == null) return null;
    if (bs.getHeader().getBlockHeight()==0) return start;
    return descend(new ChainHash(bs.getHeader().getPrevBlockHash()), depth-1);

  }


  /**
   * create concepts for blocks that could be generated based on this one.
   * Usually one block.  Sometimes two, if the prev_summary is a block just about to shard.
   */
  public List<BlockConcept> initiateBlockConcepts(BlockSummary prev_summary)
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.initiateBlockConcepts"))
    {
      
      LinkedList<BlockConcept> lst = new LinkedList<>();

      BlockHeader.Builder header_builder = BlockHeader.newBuilder();
      header_builder.setShardId( prev_summary.getHeader().getShardId() );
      header_builder.setBlockHeight( prev_summary.getHeader().getBlockHeight() +1);
      header_builder.setPrevBlockHash( prev_summary.getHeader().getSnowHash() );

      if (header_builder.getBlockHeight() >= params.getActivationHeightShards())
      {
        header_builder.setVersion(2);
      }
      else
      {
        header_builder.setVersion(1);
      }

      long time = System.currentTimeMillis();
      BigInteger target = PowUtil.calcNextTarget(prev_summary, params, time);

      header_builder.setTimestamp(time);
      header_builder.setTarget(BlockchainUtil.targetBigIntegerToBytes(target));
      header_builder.setSnowField(prev_summary.getActivatedField());

      if (ShardUtil.shardSplit(prev_summary, params))
      { // split
        for(int c : ShardUtil.getShardChildIds( prev_summary.getHeader().getShardId() ))
        {
          if (node.getInterestShards().contains(c))
          {
            try
            {
            node.openShard(c); } catch(Exception e){e.printStackTrace(); }

            header_builder.setShardId(c);
            lst.add(new BlockConcept(prev_summary, header_builder.build()));
          }
        }
      }
      else
      {
        lst.add(new BlockConcept(prev_summary, header_builder.build()));
      }
      return lst;
    }

  }

  public Block fleshOut(BlockConcept concept, SubscribeBlockTemplateRequest mine_to)
    throws ValidationException
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.fleshOut"))
    {
      Block.Builder block_builder = Block.newBuilder();
      BlockHeader.Builder header_builder = BlockHeader.newBuilder().mergeFrom(concept.getHeader());
      BlockSummary prev_summary = concept.getPrevSummary();

      ChainHash prev_utxo_root = new ChainHash(prev_summary.getHeader().getUtxoRootHash());
      if (header_builder.getShardId() != prev_summary.getHeader().getShardId())
      if (!ShardUtil.getInheritSet(header_builder.getShardId()).contains(prev_summary.getHeader().getShardId()))
      {
        // If we are a split and do not inherit, start with clean slate
        prev_utxo_root = new ChainHash(HashUtils.hashOfEmpty());
      }
      UtxoUpdateBuffer utxo_buffer = new UtxoUpdateBuffer( node.getUtxoHashedTrie(), prev_utxo_root);

      
      // Add import shards to utxo buffer
      block_builder.addAllImportedBlocks( concept.getImportedBlocks() );
      for(ImportedBlock ib : block_builder.getImportedBlocksList())
      {
        for(ImportedOutputList lst : ib.getImportOutputsMap().values())
        {
          utxo_buffer.addOutputs(lst);
        }
      }

      List<Transaction> regular_transactions = node.getMemPool(header_builder.getShardId())
        .getTransactionsForBlock(prev_utxo_root, node.getParams().getMaxBlockSize());
     
      long fee_sum = 0L;

      Set<Integer> shard_cover_set = ShardUtil.getCoverSet(header_builder.getShardId(), params);
      Map<Integer, UtxoUpdateBuffer> export_utxo_buffer = new TreeMap<>();

      for(Transaction tx : regular_transactions)
      {
         fee_sum += Validation.deepTransactionCheck(tx, utxo_buffer, header_builder.build(), params,
          shard_cover_set, export_utxo_buffer);
      }

      Transaction coinbase = BlockForge.buildCoinbase( params, header_builder.build(), fee_sum, mine_to, header_builder.getShardId());
      Validation.deepTransactionCheck(coinbase, utxo_buffer, header_builder.build(), params,
        shard_cover_set, export_utxo_buffer);

      block_builder.addTransactions(coinbase);
      block_builder.addAllTransactions(regular_transactions);

      // Save export UTXO data
      for(int export_shard_id : export_utxo_buffer.keySet())
      {
        UtxoUpdateBuffer export_buffer = export_utxo_buffer.get(export_shard_id);
        header_builder.putShardExportRootHash(export_shard_id, export_buffer.simulateUpdates().getBytes());
      }

      int tx_size_total = 0;
      LinkedList<ChainHash> tx_list = new LinkedList<ChainHash>();
      for(Transaction tx : block_builder.getTransactionsList())
      {
        tx_list.add( new ChainHash(tx.getTxHash()));
        tx_size_total += tx.getInnerData().size() + tx.getTxHash().size();
      }

      if (header_builder.getVersion() == 2)
      {
        header_builder.setTxDataSizeSum(tx_size_total);
      }

      header_builder.setMerkleRootHash( DigestUtil.getMerkleRootForTxList(tx_list).getBytes());
      header_builder.setUtxoRootHash( utxo_buffer.simulateUpdates().getBytes());

      block_builder.setHeader(header_builder.build());

      return block_builder.build();
    }
  }

  public List<BlockConcept> importShards(BlockConcept concept, Map<String, ChainHash> restrict_known_map)
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.importShards"))
    {
      LinkedList<BlockConcept> lst = new LinkedList<>();

      int shard_id = concept.getHeader().getShardId();

      Set<Integer> exclude_set = new TreeSet<>();
      exclude_set.addAll(ShardUtil.getCoverSet(shard_id, params));
      exclude_set.addAll(ShardUtil.getAllParents(shard_id));
      exclude_set.add(shard_id);

      for(int i=0; i<IMPORT_PASSES; i++)
      {
        TreeSet<Integer> shards_to_add = new TreeSet<>();
        for(int s : node.getActiveShards())
        {
          if (!exclude_set.contains(s))
          {
            shards_to_add.add(s);
          }
        }
        BlockConcept working_c = concept;
        while(shards_to_add.size() > 0)
        {
          ArrayList<Integer> shards_to_add_lst = new ArrayList<>();
          shards_to_add_lst.addAll(shards_to_add);
          Collections.shuffle(shards_to_add_lst);
          int selected_shard = shards_to_add_lst.get(0);
          
          BlockConcept imp_c = attemptImportShard(working_c, selected_shard, restrict_known_map);
          if (imp_c == null)
          {
            shards_to_add.remove(selected_shard);
          }
          else
          {
            working_c = imp_c;
          }

        }
        lst.add(working_c);
      }

      return lst;
    }

  }

  /**
   * Try to add a block from the given shard to the block concept
   * if it seems possible, return the new BlockConcept
   * else return null.
   * Note: if it returns a BlockConcept, further calls for this shard
   * may result in more blocks
   */ 
  private BlockConcept attemptImportShard(BlockConcept working_c, int selected_shard, Map<String, ChainHash> restrict_known_map)
  {
    BlockHeader cur = working_c.getShardHeads().get(selected_shard);
    if (cur == null)
    {
      cur = working_c.getShardHeads().get( ShardUtil.getShardParentId(selected_shard) );
    }
    if (cur == null) return null;

    TreeMap<BigInteger,BlockImportList> path = getPath(new ChainHash( cur.getSnowHash() ), selected_shard, restrict_known_map);
    ArrayList<BlockImportList> path_lists = new ArrayList<>();
    path_lists.addAll(path.values());
    Collections.shuffle(path_lists);


    // Use random
    BlockImportList bil = path_lists.get(0);

    // Use "best"
    bil = path.lastEntry().getValue();

    if (bil.getHeightMap().size() == 0)
    {
      return null;
    }
    TreeMap<Integer, ByteString> height_map = new TreeMap<>();
    height_map.putAll(bil.getHeightMap());
    ByteString hash = height_map.firstEntry().getValue();

    BlockSummary imp_sum = getDBSummary( hash );
    if (imp_sum == null) return null;

    BlockConcept c_add = working_c.importShard( imp_sum.getHeader() );
    if (checkCollisions(c_add))
    {
      return c_add;
    }
    else
    {
      return null;
    }

  }

  /**
   * Explore down the tree of blocks and find the path to the one with the highest work_sum
   */
  private TreeMap<BigInteger,BlockImportList> getPath(ChainHash start_point, int external_shard_id, Map<String, ChainHash> restrict_known_map)
  {
    TreeMap<BigInteger,BlockImportList> options = new TreeMap<>();

    options.put(BigInteger.ZERO, BlockImportList.newBuilder().build());

    for(ByteString next_hash : node.getDB().getChildBlockMapSet().getSet( start_point.getBytes(), 2000) )
    {
      BlockSummary bs = getDBSummary(next_hash);
      if (bs != null)
      if (bs.getHeader().getShardId() == external_shard_id)
      if (checkCollisions( restrict_known_map, bs) != null)
      {
        BigInteger work = BlockchainUtil.readInteger(bs.getWorkSum());
        BlockImportList.Builder bil = BlockImportList.newBuilder();
        bil.putHeightMap( bs.getHeader().getBlockHeight(), bs.getHeader().getSnowHash() );

        options.put(work, bil.build());

        TreeMap<BigInteger,BlockImportList> down = getPath(new ChainHash(bs.getHeader().getSnowHash()),
          external_shard_id, restrict_known_map);

        for(Map.Entry<BigInteger, BlockImportList> me : down.entrySet())
        {
          options.put( me.getKey(), bil.mergeFrom(me.getValue()).build());
        }
      }
    }

    return options;
  }


  public List<ImportedBlock> sortImportedBlocks(List<ImportedBlock> input)
  {
    TreeMap<Pair<Integer, Integer>, ImportedBlock> map = new TreeMap<>();

    for(ImportedBlock ib : input)
    {
      map.put(new Pair<Integer,Integer>(ib.getHeader().getShardId(), ib.getHeader().getBlockHeight()) ,ib);
    }
    return ImmutableList.copyOf(map.values());

  }

  /**
   * Represents a block we could flesh out and mine, if it makes sense to do so
   */
  public class BlockConcept implements Comparable<BlockConcept>
  {
    // inputs
    private BlockSummary prev_summary;
    private BlockHeader header;
    private List<ImportedBlock> imported_blocks;

    // Calculated
    private BigInteger work_sum;
    private BigInteger sort_work;
    private BigInteger rnd_val;
    private TreeMap<Integer, BlockHeader> shard_heads;
    private int advances_shard = 0;


    public BlockConcept(BlockSummary prev_summary, BlockHeader header)
    {
      this(prev_summary, header, ImmutableList.of());
    }
    public BlockConcept(BlockSummary prev_summary, BlockHeader header, List<ImportedBlock> imported_blocks )
    {
      this.prev_summary = prev_summary;
      this.header = header;
      this.imported_blocks = ImmutableList.copyOf(imported_blocks);

      if (node.getBlockIngestor(header.getShardId()).getHead() == null)
      {
        advances_shard=1;
      }
      else
      {
        if (node.getBlockIngestor(header.getShardId()).getHead().getHeader().getBlockHeight() < header.getBlockHeight())
        {
          advances_shard=1;
        }
      }

      { // Populate shard heads
        shard_heads = new TreeMap<>();

        shard_heads.putAll( prev_summary.getImportedShardsMap() );

        shard_heads.put( header.getShardId(), header);

        for(ImportedBlock ib : imported_blocks)
        {
          shard_heads.put( ib.getHeader().getShardId(), ib.getHeader() );
        }
      }

      { // calculate work estimate
        BlockSummary test_summary = BlockchainUtil.getNewSummary(
          BlockHeader.newBuilder()
            .mergeFrom(header)
            .setSnowHash(ChainHash.getRandom().getBytes())
            .build(),
          prev_summary, params,
          1, 
          3500, 
          imported_blocks);

        work_sum = BlockchainUtil.readInteger( test_summary.getWorkSum() );

        Random rnd = new Random();
        sort_work = work_sum
          .multiply(BigInteger.valueOf(1000000L))
          .add(BigInteger.valueOf(rnd.nextInt(1000000)));

        rnd_val = BigInteger.valueOf( rnd.nextLong() );
      }
    }

    /**
     * Build the same concept, but with this shard imported
     */
    public BlockConcept importShard(BlockHeader import_header)
    {
      BlockHeader.Builder new_header = BlockHeader.newBuilder();
      new_header.mergeFrom(header);

      LinkedList<ImportedBlock> lst = new LinkedList<>();
      lst.addAll(imported_blocks);
      lst.add( node.getShardUtxoImport().getImportBlockForTarget(new ChainHash( import_header.getSnowHash() ), header.getShardId()));

      // Rebuild shard import map
      int shard_import = import_header.getShardId();

      BlockImportList.Builder bil = BlockImportList.newBuilder();
      if (new_header.getShardImportMap().containsKey(shard_import))
      {
        bil.mergeFrom( new_header.getShardImportMap().get(shard_import) );
      }
      bil.putHeightMap( import_header.getBlockHeight(), import_header.getSnowHash() );
      new_header.putShardImport(shard_import, bil.build());

      return new BlockConcept(prev_summary, new_header.build(), sortImportedBlocks(lst) );

    }

    public BigInteger getWorkSum(){return work_sum; }
    public BlockHeader getHeader(){return header;}
    public BlockSummary getPrevSummary(){return prev_summary;}
    public List<ImportedBlock> getImportedBlocks(){return imported_blocks;}
    public int getAdvancesShard(){return advances_shard; }
    public int getHeight(){return getHeader().getBlockHeight(); }
    public Map<Integer, BlockHeader> getShardHeads(){return shard_heads; }
    private BigInteger getSortWork(){return sort_work; }
    private BigInteger getRandomVal(){return rnd_val;}



    /** sorting such that best block is first */
    @Override
    public int compareTo(ShardBlockForge.BlockConcept o)
    {
      // Advancing a shard is best
      if (getAdvancesShard() > o.getAdvancesShard()) return -1;
      if (getAdvancesShard() < o.getAdvancesShard()) return 1;

      if (getAdvancesShard() == 1)
      { 
        // smallest height is next best - grow the shorter shard
        if (getHeight() < o.getHeight()) return -1;
        if (getHeight() > o.getHeight()) return 1;
      }

        // larger is better
        return o.getSortWork().compareTo(getSortWork());
      

      //return getRandomVal().compareTo(o.getRandomVal());


    }
    @Override
    public String toString()
    {
      return String.format("Concept{ a:%d h:%d w:%s shard:%d imp:%d }",
        getAdvancesShard(),
        getHeight(),
        getWorkSum().toString(),
        getHeader().getShardId(),
        getImportedBlocks().size());
    }

  }

  public class ConceptUpdateThread extends PeriodicThread
  {
    public ConceptUpdateThread()
    {
      super(15000);
      setName("ShardBlockForge.ConceptUpdateThread");
    }

    public void runPass()
    {
      // If no template requests for 5 minutes, don't bother
      if (last_template_request + 300000L < System.currentTimeMillis())
      {
        current_top_concepts = null;
				tickleUserService();
        return;
      }
    
      // Source blocks to mangle
      HashSet<ChainHash> possible_source_blocks = new HashSet<>();

      // Possible blocks to mine
      TreeSet<BlockConcept> possible_set = new TreeSet<>();

        
      if (node.getBlockIngestor(0).getHead() == null)
      {
        // Let the old thing do the genesis gag
        return;
      }
        for(int shard_id : node.getActiveShards())
        {
          BlockSummary head = node.getBlockIngestor(shard_id).getHead();

          if (head != null)
          {
            int check_depth=1;
            //if (head.getHeader().getBlockHeight() < 10)
            {
              //possible_source_blocks.addAll( getBlocksAround(new ChainHash(head.getHeader().getSnowHash()), check_depth, shard_id) );
            }
          }
        }
        System.out.println("Possible source blocks: " + possible_source_blocks.size());

        possible_set.addAll(exploreBlocks(possible_source_blocks, true, ImmutableMap.of()));

        
        int depth=0;
        while (possible_set.size()==0)
        {
          Set<ChainHash> gold = getGoldenSet(depth);
          Map<String, ChainHash> gold_known_map = getKnownMap(gold);

          System.out.println("Gold restrict size: " + gold_known_map.size());
          possible_set.addAll(exploreBlocks(gold, false, ImmutableMap.copyOf(gold_known_map)));
          depth++;
          if (depth > 8) break;
        }

        //check_depth++;
      //}
    
      System.out.println("ZZZ Possible blocks: " + possible_set.size());
      int printed = 0;
      ArrayList<BlockConcept> good_concepts = new ArrayList<>();
      for(BlockConcept c : possible_set)
      {
        System.out.println("ZZZ " + c.toString());
        printed++;
        good_concepts.add(c);
        if (printed > 10) break;
      }
      current_top_concepts = good_concepts;

			tickleUserService();

      if (forge_log != null)
      {
        forge_log.println("--------------------------------");
        time_record.printReport(forge_log);
        time_record.reset();
        forge_log.println("--------------------------------");
      }


    }
		private void tickleUserService()
		{
     	SnowUserService u = node.getUserService();
      if (u != null)
      {
      	u.tickleBlocks();
      }


		}

  }

  private LRUCache<ByteString, BlockSummary> block_summary_cache = new LRUCache<>(5000);
  public BlockSummary getDBSummary(ByteString bytes)
  {
    
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.getDBSummary"))
    {
      synchronized(block_summary_cache)
      {
        BlockSummary bs = block_summary_cache.get(bytes);
        if (bs != null) return bs;

      }
      BlockSummary bs = node.getDB().getBlockSummaryMap().get(bytes);
      if (bs != null)
      {
        synchronized(block_summary_cache)
        {
          block_summary_cache.put(bytes, bs);
        }

      }

      return bs;
    }
  }

  public BlockSummary getDBSummary(ChainHash hash)
  {
    return getDBSummary(hash.getBytes());
  }
 
  public void tickle()
  {
    concept_update_thread.wake();
  }



}
