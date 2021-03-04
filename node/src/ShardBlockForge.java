package snowblossom.node;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import duckutil.Pair;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Collections;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;
import snowblossom.lib.*;
import snowblossom.lib.trie.HashUtils;
import snowblossom.proto.*;

public class ShardBlockForge
{

  private SnowBlossomNode node;
  private NetworkParams params;
  private static final Logger logger = Logger.getLogger("snowblossom.node");
  
  public ShardBlockForge(SnowBlossomNode node)
  {
    this.node = node;
    this.params = node.getParams();
  }

  public Block getBlockTemplate(SubscribeBlockTemplateRequest mine_to)
  {
    HashSet<ChainHash> possible_source_blocks = new HashSet<>();

    for(int shard_id : node.getActiveShards())
    {
      BlockSummary head = node.getBlockIngestor(shard_id).getHead();

      // This handles the case of starting a new shard
      if ((head == null) && (shard_id == 0))
      {
        // Let the old thing do the genesis gag
        return node.getBlockForge(0).getBlockTemplate(mine_to);
      }
      if (head != null)
      {
        possible_source_blocks.addAll( getBlocks(new ChainHash(head.getHeader().getSnowHash()), 6, shard_id) );
      }
    }

    LinkedList<BlockConcept> concept_list = new LinkedList<>();
    for(ChainHash hash : possible_source_blocks)
    {
      BlockSummary bs = node.getDB().getBlockSummaryMap().get(hash.getBytes());
      if (bs != null)
      {
        concept_list.addAll( initiateBlockConcepts(bs) );
      }
    }

    TreeSet<BlockConcept> possible_set = new TreeSet<>();
    for(BlockConcept bc : concept_list)
    {
      for(BlockConcept bc_with : importShards(bc))
      {
        if (testBlock(bc_with))
        {

          possible_set.add(bc_with);
        }
      }
    }
  
    System.out.println("ZZZ Possible blocks: " + possible_set.size());
    for(BlockConcept c : possible_set)
    {
      System.out.println("ZZZ " + c.toString());
    }
    if (possible_set.size() == 0) return null; 

    BlockConcept selected = possible_set.first();

    try
    {
      return fleshOut(selected, mine_to); 

    }
    catch(ValidationException e)
    {
      logger.warning("Validation failed: " + e);
      return null;
    }


  }

  private boolean checkCollisions(BlockConcept b)
  {
    TreeMap<String, ChainHash> known_map = new TreeMap<>();

    try
    {
      Validation.checkCollisions(known_map, b.getPrevSummary().getShardHistoryMap());
      Validation.checkCollisions(known_map, b.getHeader().getShardImportMap());
    }
    catch(ValidationException e)
    {
      return false;
    }

    return true;
  }

  private boolean testBlock(BlockConcept b)
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
      logger.info("Validation failed: " + e);
      return false;
    }
  }

  /**
   * Go down by depth and then return all blocks that are decendend from that one
   * and are on the same shard id.
   */
  public Set<ChainHash> getBlocks(ChainHash start, int depth, int shard_id)
  {
    ChainHash tree_root = descend(start, shard_id);

    return climb(tree_root, shard_id);


  }

  public Set<ChainHash> climb(ChainHash start, int shard_id)
  {
    HashSet<ChainHash> set = new HashSet<>();
    BlockSummary bs = node.getDB().getBlockSummaryMap().get(start.getBytes());
  
    if (bs != null)
    if (bs.getHeader().getShardId() == shard_id)
    {
      set.add(new ChainHash(bs.getHeader().getSnowHash()));
    }

    for(ByteString next : node.getDB().getChildBlockMapSet().getSet(start.getBytes(), 20))
    {
      set.addAll(climb(new ChainHash(next), shard_id));
    }

    return set;
    
  }

  public ChainHash descend(ChainHash start, int depth)
  {
    if (depth==0) return start;

    BlockSummary bs = node.getDB().getBlockSummaryMap().get(start.getBytes());
    if (bs == null) return null;
    return descend(new ChainHash(bs.getHeader().getPrevBlockHash()), depth-1);

  }


  /**
   * create concepts for blocks that could be generated based on this one.
   * Usually one block.  Sometimes two, if the prev_summary is a block just about to shard.
   */
  public List<BlockConcept> initiateBlockConcepts(BlockSummary prev_summary)
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

  public Block fleshOut(BlockConcept concept, SubscribeBlockTemplateRequest mine_to)
    throws ValidationException
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

  public List<BlockConcept> importShards(BlockConcept concept)
  {
    LinkedList<BlockConcept> lst = new LinkedList<>();
    
    lst.add(concept); // no imports - why not

    int shard_id = concept.getHeader().getShardId();

    Set<Integer> exclude_set = new TreeSet<>();
    exclude_set.addAll(ShardUtil.getCoverSet(shard_id, params));
    exclude_set.addAll(ShardUtil.getAllParents(shard_id));
    exclude_set.add(shard_id);



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
      
      BlockConcept imp_c = attempImportShard(working_c, selected_shard);
      if (imp_c == null)
      {
        shards_to_add.remove(selected_shard);
      }
      else
      {
        working_c = imp_c;
        //lst.add(working_c);
      }

    }
    lst.add(working_c);

    return lst;

  }

  /**
   * Try to add a block from the given shard to the block concept
   * if it seems possible, return the new BlockConcept
   * else return null.
   * Note: if it returns a BlockConcept, further calls for this shard
   * may result in more blocks
   */ 
  private BlockConcept attempImportShard(BlockConcept working_c, int selected_shard)
  {
    BlockHeader cur = working_c.getShardHeads().get(selected_shard);
    if (cur == null)
    {
      cur = working_c.getShardHeads().get( ShardUtil.getShardParentId(selected_shard) );
    }
    if (cur == null) return null;

    TreeMap<BigInteger,BlockImportList> path = getPath(new ChainHash( cur.getSnowHash() ), selected_shard);
    BlockImportList bil = path.lastEntry().getValue();
    if (bil.getHeightMap().size() == 0)
    {
      return null;
    }
    TreeMap<Integer, ByteString> height_map = new TreeMap<>();
    height_map.putAll(bil.getHeightMap());
    ByteString hash = height_map.firstEntry().getValue();

    BlockSummary imp_sum = node.getDB().getBlockSummaryMap().get( hash );
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
  private TreeMap<BigInteger,BlockImportList> getPath(ChainHash start_point, int external_shard_id)
  {
    TreeMap<BigInteger,BlockImportList> options = new TreeMap<>();

    options.put(BigInteger.ZERO, BlockImportList.newBuilder().build());

    for(ByteString next_hash : node.getDB().getChildBlockMapSet().getSet( start_point.getBytes(), 20) )
    {
      BlockSummary bs = node.getDB().getBlockSummaryMap().get(next_hash);
      if (bs != null)
      if (bs.getHeader().getShardId() == external_shard_id)
      {
        BigInteger work = BlockchainUtil.readInteger(bs.getWorkSum());
        BlockImportList.Builder bil = BlockImportList.newBuilder();
        bil.putHeightMap( bs.getHeader().getBlockHeight(), bs.getHeader().getSnowHash() );

        options.put(work, bil.build());

        TreeMap<BigInteger,BlockImportList> down = getPath(new ChainHash(bs.getHeader().getSnowHash()),
          external_shard_id);

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

        // larger is better
        return o.getSortWork().compareTo(getSortWork());
      }

      return getRandomVal().compareTo(o.getRandomVal());


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

}
