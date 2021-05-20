package snowblossom.node;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
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

  private Dancer dancer;

  private LRUCache<ChainHash, Boolean> signature_cache = new LRUCache<>(2000);

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

    this.dancer = new Dancer(node);

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

  private void considerAdd(Set<BlockConcept> concepts, BlockConcept bc)
  {
    if (!bc.isComplete())
    {
      System.out.println("Rejecting incomplete: " + bc);
      System.out.println(bc.toStringFull());
      return;
    }

    ChainHash sig = bc.getSig();

    synchronized(signature_cache)
    {
      if (signature_cache.containsKey( sig ))
      {
        System.out.println("Duplicate: " + bc);
        return;
      }
    }
    concepts.add(bc);

  }

  private Set<BlockConcept> exploreCoordinator(int coord_shard)
    throws ValidationException
  {
    TreeSet<BlockConcept> concepts = new TreeSet<>();
    // We are just assuming the current head is following the dance.
    // Things will get real weird real quick if not.

    for(BlockHeader prev_header : node.getForgeInfo().getShardHeads(coord_shard))
    {
      BlockSummary prev = node.getForgeInfo().getSummary( prev_header.getSnowHash() );

      synchronized(signature_cache)
      {
        signature_cache.put( getSignature(prev), true);
      }

      List<BlockConcept> concept_list = initiateBlockConcepts(prev);

      for(BlockConcept bc : concept_list)
      {
        System.out.println("exploreCoordinator"+coord_shard+": " + bc.toString());
        // If it is a shard we actually work on
        if (node.getInterestShards().contains(bc.getHeader().getShardId()))
        {
          // If we are splitting into a non-coordinator shard, don't add any other imports
          if (isCoordinator(bc.getHeader().getShardId()))
          {
            // Make a list sorted by block height of things we could possible include
            ListMultimap<Integer, BlockHeader> possible_import_blocks =
                   MultimapBuilder.treeKeys().arrayListValues().build();
            for(BlockHeader current_import_head : prev.getImportedShardsMap().values())
            {
              Set<ChainHash> possible_hashes = 
                node.getForgeInfo().climb( new ChainHash(current_import_head.getSnowHash()), -1);
              for(ChainHash ch : possible_hashes)
              {
                BlockHeader blk_h = node.getForgeInfo().getHeader(ch);
                if (blk_h != null)
                {
                  // exclude things that import coord blocks that do not match this one
                  boolean invalid_coord_import =false;
                  if (blk_h.getShardImportMap().containsKey(coord_shard))
                  {
                    for(ByteString hash : blk_h.getShardImportMap().get(coord_shard).getHeightMap().values())
                    {
                      BlockHeader check = node.getForgeInfo().getHeader(new ChainHash(hash));
                      if ((check==null) || (!node.getForgeInfo().isInChain( prev_header, check)))
                      {
                        invalid_coord_import = true;
                      }
                    }

                  }

                  if (!invalid_coord_import)
                  {
                    possible_import_blocks.put( blk_h.getBlockHeight(), blk_h );
                  }
                  
                }
              }
            }

            for(BlockHeader imp_blk : possible_import_blocks.values())
            {
              // Not in the cover set from this coordinator
              if (!ShardUtil.getCoverSet(coord_shard, node.getParams()).contains(imp_blk.getShardId()))
              {
                List<BlockHeader> imp_seq = node.getForgeInfo().getImportPath(bc.getShardHeads(), imp_blk);
                if (dancer.isCompliant(imp_blk))
                if (imp_seq != null)
                if (imp_seq.size() == 1)
                {
                  BlockHeader join_point = node.getForgeInfo().getLatestShard(imp_blk, coord_shard);
                  if (node.getForgeInfo().isInChain(prev_header, join_point))
                  {
                    bc = bc.importShard(imp_blk);
                  }
                }
              }
            }

          }

          considerAdd(concepts, bc);

        }

      }

    }

    return concepts;

  }
  

  private Set<BlockConcept> exploreFromCoordinatorHead(int coord_shard)
    throws ValidationException
  {
    System.out.println("exploreFromCoordinatorHead(" + coord_shard +")");
    TreeSet<BlockConcept> concepts = new TreeSet<>();
    // Start with the highest block for the coordinator shard
    // Then take the set of import blocks <integer,blockhead> and descend from all of those
    // Those are our potential block parents
    // Build concepts from them all.
    // Or just the leafs?

    // Since we start with what we have included, any of these blocks are potentially valid
    // to be included in future coordinators as long as they don't include other coordinator 
    // forks

    // TOOD - replace with multi-head maybe
    for(BlockHeader coord_head : node.getForgeInfo().getShardHeads(coord_shard))
    {

      // Starting from the more recent coordinator head
      // Find all the imported shard heads

      // Note: the 2x is there because we might be at height X and some other shard is at X-skew-2 or something
      // We can still build a block by bringing in more recent blocks on that shard to bring it to within skew
      Map<Integer, BlockHeader> import_heads = node.getForgeInfo().getImportedShardHeads( coord_head, node.getParams().getMaxShardSkewHeight()*2);

      HashSet<ChainHash> possible_prevs = new HashSet<>();

      // For each imported shard head, get all the new blocks under each
      for(int src_shard : import_heads.keySet())
      {
        if (node.getInterestShards().contains(src_shard))
        {
          ChainHash h = new ChainHash( import_heads.get(src_shard).getSnowHash() );
          possible_prevs.addAll( node.getForgeInfo().climb(h, src_shard) );
        }
      }
      System.out.println("Possible_prevs: " + possible_prevs.size());

      int exclude_inchain=0;
      int exclude_imppath=0;

      for(ChainHash prev_hash : possible_prevs)
      {
        BlockSummary prev = node.getForgeInfo().getSummary( prev_hash );
        if (prev == null) continue;
        synchronized(signature_cache)
        {
          signature_cache.put( getSignature(prev), true);
        }
        int prev_shard = prev.getHeader().getShardId();
        int prev_height = prev.getHeader().getBlockHeight();
        if (import_heads.containsKey(prev_shard))
        {
          // If this block is not in the chain from whatever the import_head has,
          // don't bother with it
          // We get into this state from a climb from a parent shard into a shard that we
          // already have some locked information for
          if (!node.getForgeInfo().isInChain(prev.getHeader(), import_heads.get(prev_shard))) {exclude_inchain++; continue; }
          
        }
        List<BlockHeader> coord_imp_lst = node.getForgeInfo().getImportPath(prev, coord_head);
        if (coord_imp_lst == null) {exclude_imppath++; continue;}

        List<BlockConcept> concept_list = initiateBlockConcepts(prev);

        for(BlockConcept bc : concept_list)
        {
          System.out.println("Considering: " + bc);
          int bc_shard = bc.getHeader().getShardId();

          if (!node.getInterestShards().contains(bc_shard)) continue;

          // Already have a block of this shard at this height
          if ((import_heads.containsKey(bc_shard)) && (import_heads.get(bc_shard).getBlockHeight() >= bc.getHeader().getBlockHeight())) continue;

          // Add as many as are in compliance
          for(BlockHeader h : coord_imp_lst)
          {
            if (bc == null) break;
            if (dancer.isCompliant(h))
            {
              Map<Integer, BlockHeader> cur_imp_heads = node.getForgeInfo().getImportedShardHeads( h, node.getParams().getMaxShardSkewHeight()+2);

              for(BlockHeader imp_h : cur_imp_heads.values())
              {
                if (!ShardUtil.getCoverSet(bc.getHeader().getShardId(), node.getParams())
                  .contains(imp_h.getShardId()))
                {
                  List<BlockHeader> path = node.getForgeInfo().getImportPath(bc.getShardHeads(), imp_h);
                  if (path == null)
                  {

                    // We have to be able to import the coordinator, or no point
                    // but maybe we want to build a block without any imports
                    // bah
                    if(Dancer.isCoordinator(imp_h.getShardId()))
                    {
                      bc= null;
                      break;
                    }

                  }

                  if (path != null)
                  {
                    for(BlockHeader h_imp : path)
                    {
                      if (ShardUtil.getCoverSet(bc.getHeader().getShardId(), node.getParams())
                                      .contains(h_imp.getShardId()))
                      { // Can't import block in my coverset
                        break;
                      }
                      bc = bc.importShard(h_imp);
                    }
                  }
                }

              }

            }
            else
            {
              break;
            }
          }
          if (bc != null)
          {
            considerAdd(concepts, bc);
          }

        }

      }
      
      System.out.println("exclude_inchain: " + exclude_inchain);
      System.out.println("exclude_imppath: " +  exclude_imppath);
    }

    return concepts;

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
        header_builder.setTxCount(tx_list.size());
      }

      header_builder.setMerkleRootHash( DigestUtil.getMerkleRootForTxList(tx_list).getBytes());
      header_builder.setUtxoRootHash( utxo_buffer.simulateUpdates().getBytes());

      block_builder.setHeader(header_builder.build());

      return block_builder.build();
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

  public boolean isCoordinator(int shard)
  {
    return dancer.isCoordinator(shard);
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
          advances_shard = 1;
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

        // Count as advancing if we are the highest under current coordinator
        // get coordinator import
        // see what the longest 

        BlockHeader boss_coordinator = node.getForgeInfo().getHighestCoordinator(shard_heads.values());

        if (!Dancer.isCoordinator(header.getShardId()))
        if (boss_coordinator!=null)
        {
          Map<Integer, BlockHeader> coord_imports = node.getForgeInfo().getImportedShardHeads(
            boss_coordinator, node.getParams().getMaxShardSkewHeight() +2);
          BlockHeader this_shard_import = coord_imports.get(header.getShardId());
          if (this_shard_import != null)
          {
            int highest = 0;
            for(ChainHash hash : node.getForgeInfo().climb( new ChainHash(this_shard_import.getSnowHash()), -1))
            {
              BlockHeader h = node.getForgeInfo().getHeader(hash);
              if (h != null)
              {
                highest = Math.max(highest, h.getBlockHeight());
              }
            }
            if (header.getBlockHeight() > highest) advances_shard=1;
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
      throws ValidationException
    {
      try(TimeRecordAuto tra_gbt = TimeRecord.openAuto("ShardBlockForge.bc.importShard"))
      {

        { // check validity of action
          int shard_id=import_header.getShardId();
          int height = import_header.getBlockHeight();
          if (getShardHeads().containsKey(shard_id))
          {
            BlockHeader prev = getShardHeads().get(shard_id);
            if (prev.getBlockHeight() +1 != height)
            {
              throw new ValidationException("Illegal import, wrong heights");
            }
            if (!prev.getSnowHash().equals(import_header.getPrevBlockHash()))
            {
              throw new ValidationException("Illegal import, wrong parent");
            }
          }
          if (import_header.getSnowHash().size() != 32)
          {
            throw new ValidationException("No hash");
          }
        }
        // TODO - add a quick conflict check

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

    public boolean isComplete()
    {
      return Validation.checkBraidCompleteness(getHeight(), node.getParams(), getShardHeads(), 0);
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
      //return o.getSortWork().compareTo(getSortWork());
      

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

    public String toStringFull()
    {
      StringBuilder sb = new StringBuilder();
      sb.append(toString());
      sb.append('\n');

      for(Map.Entry<Integer, BlockHeader> me : getShardHeads().entrySet())
      {
        sb.append("  " + me.getKey());
        sb.append(" - ");
        BlockHeader h = me.getValue();
        String hash = "blank";
        if (h.getSnowHash().size() >0) hash = new ChainHash(h.getSnowHash()).toString();

        sb.append(String.format("h:%d s:%d %s", h.getBlockHeight(), h.getShardId(), hash));
        sb.append("\n");
      }
      return sb.toString();

    }

    // Return some bytes that will be duplicated if we already tried basically the same block
    // before.
    public ChainHash getSig()
    {
      return getSignature(getHeader(), getShardHeads());
    }

  }

  // Return some bytes that will be duplicated if we already tried basically the same block
  // before.
  public ChainHash getSignature(BlockHeader h, Map<Integer, BlockHeader> import_map)
  {
    StringBuilder sb = new StringBuilder();
    sb.append(" parent:" + new ChainHash(h.getPrevBlockHash()));
    sb.append(" shard:" + h.getShardId());
    for(Map.Entry<Integer, BlockHeader> me : import_map.entrySet())
    {
      if (me.getKey() != h.getShardId())
      {
        sb.append(" " + new ChainHash(me.getValue().getSnowHash()));
      }
    }

    ByteString bytes = ByteString.copyFrom(sb.toString().getBytes());
    return new ChainHash( DigestUtil.hash(bytes) );

  }

  public ChainHash getSignature(BlockSummary summary)
  {
    return getSignature(summary.getHeader(), summary.getImportedShardsMap());
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
      throws Exception
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
    

      // Possible blocks to mine
      TreeSet<BlockConcept> possible_set = new TreeSet<>();
        
      if (node.getBlockIngestor(0).getHead() == null)
      {
        // Let the old thing do the genesis gag
        return;
      }

      for(int src_shard : node.getCurrentBuildingShards())
      {
        if (isCoordinator(src_shard))
        { // Coordinator dance
          possible_set.addAll( exploreCoordinator(src_shard) );
        }
      }
      int highest_coord = 0;
      for(int coord : node.getForgeInfo().getNetworkActiveShards().keySet())
      {
        if (isCoordinator(coord))
        {
          highest_coord = Math.max(highest_coord, coord);
        }
      }
      possible_set.addAll(exploreFromCoordinatorHead(highest_coord));



      System.out.println("ZZZ Possible blocks: " + possible_set.size() + " on " + node.getCurrentBuildingShards());
      int printed = 0;
      ArrayList<BlockConcept> good_concepts = new ArrayList<>();
      for(BlockConcept c : possible_set)
      {
        if (printed < 10)
        {
          System.out.println("ZZZ " + c.toString());
        }
        printed++;
        good_concepts.add(c);
        if (printed > 80) break;
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



}
