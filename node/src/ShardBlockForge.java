package snowblossom.node;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import duckutil.LRUCache;
import duckutil.Pair;
import duckutil.PeriodicThread;
import duckutil.TimeRecord;
import duckutil.TimeRecordAuto;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
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
import snowblossom.lib.*;
import snowblossom.lib.trie.HashUtils;
import snowblossom.proto.*;

public class ShardBlockForge
{

  public static final int IMPORT_PASSES=1;

  private SnowBlossomNode node;
  private NetworkParams params;
  private static final Logger logger = Logger.getLogger("snowblossom.node");
 
  private final PrintStream forge_log;
  private TimeRecord time_record;

  private volatile long last_template_request = 0L;
  private volatile ArrayList<BlockConcept> current_top_concepts = null;
  private ConceptUpdateThread concept_update_thread;

  private ByteString GOLD_MAP_KEY = ByteString.copyFrom("forge".getBytes());

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

      if ((possible_set == null) || (possible_set.size() == 0))
      {
        concept_update_thread.wake();
      }
      if (possible_set == null) return null;
      if (possible_set.size() == 0) return null; 

      Random rnd = new Random();

      // Random
      //BlockConcept selected = possible_set.get( rnd.nextInt(possible_set.size()) );

      // First 4
      //BlockConcept selected = possible_set.get( rnd.nextInt(Math.min(4,possible_set.size())) );
      
      // First
      BlockConcept selected = possible_set.get(0);

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
  private Set<BlockConcept> exploreBlocks(Collection<BlockSummary> possible_source_blocks, boolean require_better, Map<String, ChainHash> restrict_known_map)
  {
    // TODO - maybe sort by block height and select the first few rather than exploring entire space
    // like some sort of crab beast

    ContextCache cc = new ContextCache();

    // Concepts to explore
    LinkedList<BlockConcept> concept_list = new LinkedList<>();

    for(BlockSummary bs : possible_source_blocks)
    {
      if (node.getInterestShards().contains(bs.getHeader().getShardId()))
      {
        concept_list.addAll( initiateBlockConcepts(bs) );
      }
    }

    Collections.shuffle(concept_list);

    TreeSet<BlockConcept> possible_set = new TreeSet<>();

    for(BlockConcept bc : concept_list)
    {
      for(BlockConcept bc_with : importShards(cc, bc, restrict_known_map))
      {
        if (testBlock(bc_with))
        {
          if ((!require_better) || isBetter(bc_with))
          {
            possible_set.add(bc_with);
          }
        }
      }
      if ((possible_set.size() > 50) && (possible_set.first().getAdvancesShard() > 0))
      {
        break;
      }

    }

    return possible_set;

  }

  private boolean checkCollisions(BlockConcept b)
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.checkCollisions(b)"))
    {
      HashMap<String, ChainHash> known_map = new HashMap<>(8192,0.5f);
      
      // Check all imported shard headers
      for(ImportedBlock ib : b.getImportedBlocks())
      {
        BlockHeader h = ib.getHeader();

        if(!Validation.checkCollisionsNT(known_map, h.getShardId(), h.getBlockHeight(), new ChainHash(h.getSnowHash()))) return false;
        if(!Validation.checkCollisionsNT(known_map, h.getShardImportMap())) return false;
      }

      if (Validation.checkCollisionsNT(known_map, b.getPrevSummary().getShardHistoryMap()))
      if (Validation.checkCollisionsNT(known_map, b.getHeader().getShardImportMap()))
      if (Validation.checkCollisionsNT(known_map, b.getHeader().getShardId(), b.getHeader().getBlockHeight(), ChainHash.ZERO_HASH))
      {
        return true;
      }
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



  public Map<String, ChainHash> getKnownMap(Collection<BlockSummary> lst)
  {
    Map<String, ChainHash> known_map = new HashMap<>(2048,0.5f);

    for(BlockSummary bs : lst)
    {
      known_map = checkCollisions(known_map, bs);
      if (known_map == null) throw new RuntimeException("getKnownMap on conflicting data set");

    }
    return known_map;

  }


  private Map<String, ChainHash> checkCollisions(Map<String, ChainHash> known_map_in, BlockSummary bs)
  {

    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.checkCollisions(m,bs)"))
    {
      OverlayMap<String, ChainHash> known_map = new OverlayMap<>(known_map_in, true);

      //HashMap<String, ChainHash> known_map = new HashMap<>(2048,0.5f);
      //known_map.putAll( known_map_in );

      BlockHeader h = bs.getHeader();

      if (Validation.checkCollisionsNT(known_map, bs.getShardHistoryMap()))
      if (Validation.checkCollisionsNT(known_map, h.getShardId(), h.getBlockHeight(), new ChainHash(h.getSnowHash())))
      if (Validation.checkCollisionsNT(known_map, h.getShardImportMap()))
      return known_map;

      return null;
    }

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

      long time = System.currentTimeMillis();
      BigInteger target = PowUtil.calcNextTarget(prev_summary, params, time);
      header_builder.setTimestamp(time);
      header_builder.setTarget(BlockchainUtil.targetBigIntegerToBytes(target));
 
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

      
      // Copy in just for size estimate - we will do it again later
      block_builder.setHeader(header_builder.build());

      int max_tx_fill_size = node.getParams().getMaxBlockSize() - block_builder.build().toByteString().size() - 8192;


      List<Transaction> regular_transactions = node.getMemPool(header_builder.getShardId())
        .getTransactionsForBlock(prev_utxo_root, max_tx_fill_size);
     
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

  public List<BlockConcept> importShards(ContextCache cc, BlockConcept concept, Map<String, ChainHash> restrict_known_map)
  {
    try(TimeRecordAuto tra_blk = TimeRecord.openAuto("ShardBlockForge.importShards"))
    {

      // TODO - retool this such that we don't have to check for collisions from scratch each time
      // it should start from the current knownmap and then add to it as shards are added.

      LinkedList<BlockConcept> lst = new LinkedList<>();
      int shard_id = concept.getHeader().getShardId();

      { // make sure we are not already on the restrict list
        OverlayMap<String, ChainHash> kmap = new OverlayMap<>(restrict_known_map, false);

        if (!Validation.checkCollisionsNT(kmap, shard_id, concept.getHeader().getBlockHeight(), ChainHash.ZERO_HASH))
        {
          return lst;
        }
      }

      Set<Integer> exclude_set = new TreeSet<>();
      exclude_set.addAll(ShardUtil.getCoverSet(shard_id, params));
      exclude_set.addAll(ShardUtil.getAllParents(shard_id));
      exclude_set.add(shard_id);

      for(int i=0; i<IMPORT_PASSES; i++)
      {
        TreeSet<Integer> shards_to_add = new TreeSet<>();
        for(int s : node.getForgeInfo().getNetworkActiveShards().keySet())
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
          
          BlockConcept imp_c = attemptImportShard(cc, working_c, selected_shard, restrict_known_map);
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
  private BlockConcept attemptImportShard(ContextCache cc, BlockConcept working_c, int selected_shard, Map<String, ChainHash> restrict_known_map)
  {
    try(TimeRecordAuto tra_is = TimeRecord.openAuto("ShardBlockForge.attemptImportShard"))
    {
      BlockHeader cur = working_c.getShardHeads().get(selected_shard);
      if (cur == null)
      {
        cur = working_c.getShardHeads().get( ShardUtil.getShardParentId(selected_shard) );
      }
      if (cur == null) return null;

      TreeMap<BigInteger,BlockImportList> path;
      
      try(TimeRecordAuto tra_path = TimeRecord.openAuto("ShardBlockForge.attemptImportShard_path"))
      {
        path = getPathCached(cc, new ChainHash( cur.getSnowHash() ), selected_shard, restrict_known_map);
      }
      ArrayList<BlockImportList> path_lists = new ArrayList<>();
      try(TimeRecordAuto tra_path = TimeRecord.openAuto("ShardBlockForge.attemptImportShard_path_shuffle"))
      {
        path_lists.addAll(path.values());
        Collections.shuffle(path_lists);
      }

      // Use random
      BlockImportList bil = path_lists.get(0);

      // Use "best"
      bil = path.lastEntry().getValue();

      if (bil.getHeightMap().size() == 0)
      {
        return null;
      }
      TreeMap<Integer, ByteString> height_map = new TreeMap<>();
      try(TimeRecordAuto tra_path = TimeRecord.openAuto("ShardBlockForge.attemptImportShard_heightmap"))
      {
        height_map.putAll(bil.getHeightMap());
      }
      ByteString hash = height_map.firstEntry().getValue();

      BlockHeader imp_head = node.getForgeInfo().getHeader(new ChainHash(hash));
      if (imp_head == null) return null;

      BlockConcept c_add = working_c.importShard( imp_head );
      if (checkCollisions(c_add))
      {
        return c_add;
      }
      else
      {
        return null;
      }
    }

  }


  private TreeMap<BigInteger,BlockImportList> getPathCached(ContextCache cc, ChainHash start_point, int external_shard_id, Map<String, ChainHash> restrict_known_map)
  {
    String key = "" +start_point + "_" + external_shard_id;

    synchronized(cc.get_path_cache)
    {
      if (cc.get_path_cache.containsKey(key))
      {
        return cc.get_path_cache.get(key);
      }
    }

    TreeMap<BigInteger,BlockImportList> m = getPath(start_point, external_shard_id, restrict_known_map);

    synchronized(cc.get_path_cache)
    {
      cc.get_path_cache.put(key, m);
    }
    return m;

  }

  /**
   * Explore down the tree of blocks and find the path to the one with the highest work_sum
   */
  private TreeMap<BigInteger,BlockImportList> getPath(ChainHash start_point, int external_shard_id, Map<String, ChainHash> restrict_known_map)
  {
    try(TimeRecordAuto tra_gbt = TimeRecord.openAuto("ShardBlockForge.getPath"))
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
      try(TimeRecordAuto tra = TimeRecord.openAuto("ShardBlockForge.bc()"))
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

        try(TimeRecordAuto tra_work = TimeRecord.openAuto("ShardBlockForge.bc().workest"))
        { // calculate work estimate

          work_sum = BlockchainUtil.getWorkForSummary(
            BlockHeader.newBuilder()
              .mergeFrom(header)
              .setSnowHash(ChainHash.getRandom().getBytes())
              .build(),
            prev_summary, params,
            imported_blocks);

          Random rnd = new Random();
          sort_work = work_sum
            .multiply(BigInteger.valueOf(1000000L))
            .add(BigInteger.valueOf(rnd.nextInt(1000000)));

          rnd_val = BigInteger.valueOf( rnd.nextLong() );
        }
      }
    }

    /**
     * Build the same concept, but with this shard imported
     */
    public BlockConcept importShard(BlockHeader import_header)
    {
      try(TimeRecordAuto tra_gbt = TimeRecord.openAuto("ShardBlockForge.bc.importShard"))
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
    private Random rnd = new Random();

    public ConceptUpdateThread()
    {
      super(15000);
      setName("ShardBlockForge.ConceptUpdateThread");

    }

    public void runPass()
    {
      
      if (forge_log != null)
      {
        //time_record.reset();
      }
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

      Map<Integer, BlockHeader> gold_set = node.getGoldSetFinder().getGoldenSet(8);

      if (gold_set != null)
      {
        Map<String, ChainHash> gold_known_map = node.getGoldSetFinder().getKnownMap(gold_set);
        System.out.println("Gold restrict size: " + gold_known_map.size());
        List<BlockSummary> source_blocks = new LinkedList<>();
        for(BlockHeader h : gold_set.values())
        {
          BlockSummary bs = node.getForgeInfo().getSummary(h.getSnowHash());
          if (bs != null) source_blocks.add(bs);
        }

        possible_set.addAll(exploreBlocks(source_blocks, false, gold_known_map));
      }

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
  }
  private void tickleUserService()
  {
    SnowUserService u = node.getUserService();
    if (u != null)
    {
      u.tickleBlocks();
    }
  }


  public BlockSummary getDBSummary(ByteString bytes)
  {
    return node.getForgeInfo().getSummary(bytes);
  }

  public BlockSummary getDBSummary(ChainHash hash)
  {
    return node.getForgeInfo().getSummary(hash);
  }
 
  public void tickle(BlockSummary bs)
  {
    // TODO - prune concepts
    ArrayList<BlockConcept> cur_top = current_top_concepts;
    if (cur_top!=null)
    {
      ArrayList<BlockConcept> pruned_concepts = new ArrayList<>();
      for(BlockConcept bc : cur_top)
      {
        if ((bs.getHeader().getShardId() != bc.getHeader().getShardId()) 
          ||
        (bs.getHeader().getBlockHeight() != bc.getHeader().getBlockHeight()))
        {
          pruned_concepts.add(bc);
        }
      }
      current_top_concepts = pruned_concepts;
      logger.info(String.format("Pruned block concepts. Previous: %d, Now: %d", cur_top.size(), pruned_concepts.size()));
    }
    concept_update_thread.wake();

    // Notify miners
    tickleUserService();

  }


  /**
   * Holder for various cache context data
   * Expected to be single threaded access only
   */
  public class ContextCache
  {
   
    LRUCache<String, TreeMap<BigInteger,BlockImportList>> get_path_cache = new LRUCache<>(5000);
    

  }

}
