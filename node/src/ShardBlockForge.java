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
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;
import duckutil.MetricLog;
import snowblossom.lib.*;
import snowblossom.lib.trie.HashUtils;
import snowblossom.proto.*;
import java.util.Collections;

public class ShardBlockForge
{

  private SnowBlossomNode node;
  private NetworkParams params;
  private static final Logger logger = Logger.getLogger("snowblossom.node");

  private volatile long last_template_request = 0L;
  private volatile ArrayList<BlockConcept> current_top_concepts = null;
  private ConceptUpdateThread concept_update_thread;

  private Dancer dancer;

  private LRUCache<ChainHash, Boolean> signature_cache = new LRUCache<>(2000);

  public ShardBlockForge(SnowBlossomNode node)
    throws Exception
  {
    this.node = node;
    this.params = node.getParams();

    this.dancer = new Dancer(node);

    concept_update_thread = new ConceptUpdateThread();
    concept_update_thread.start();

  }

  public BlockTemplate getBlockTemplate(SubscribeBlockTemplateRequest mine_to)
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

      /*if ((possible_set == null) || (possible_set.size() == 0))
      {
        concept_update_thread.wake();
      }*/

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
        Block block = fleshOut(selected, mine_to);
        if (block != null)
        {

          return BlockTemplate.newBuilder()
            .setBlock(block)
            .setAdvancesShard(selected.getAdvancesShard())
            .build();
        }
        else
        {
          return null;
        }

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
      //System.out.println("Rejecting incomplete: " + bc);
      //System.out.println(bc.toStringFull());
      return;
    }

    ChainHash sig = bc.getSig();

    synchronized(signature_cache)
    {
      if (signature_cache.containsKey( sig ))
      {
        //System.out.println("Duplicate: " + bc);
        return;
      }
    }
    concepts.add(bc);

  }

  private Set<BlockConcept> exploreCoordinator(int coord_shard, MetricLog mlog_parent)
    throws ValidationException
  {
    try(MetricLog mlog = new MetricLog(mlog_parent, "exploreCoordinator"))
    {
      mlog.set("shard", coord_shard);

      TreeSet<BlockConcept> concepts = new TreeSet<>();
      // We are just assuming the current head is following the dance.
      // Things will get real weird real quick if not.

      Collection<BlockHeader> prev_heads = node.getForgeInfo().getShardHeads(coord_shard);
      mlog.set("head_count", prev_heads.size());
      logger.info("Head count: " + prev_heads.size());

      //while(concepts.size() == 0)
      {
        //HashSet<ChainHash> parent_hash_set = new HashSet<>();
        for(BlockHeader prev_header : prev_heads)
        {
          exploreCoordinatorSpecific(prev_header, coord_shard, concepts);
          //parent_hash_set.add(new ChainHash(prev_header.getPrevBlockHash()));
        }

        // In case we have no good concepts, build something from a parent
        // failure is not an option
        /*LinkedList<BlockHeader> parent_heads = new LinkedList<>();
        for(ChainHash hash : parent_hash_set)
        {
          BlockHeader bh = node.getForgeInfo().getHeader(hash);
          if (bh != null)
          {
            parent_heads.add(bh);
          }
        }
        prev_heads = parent_heads;*/

      }
      mlog.set("count", concepts.size());

      return concepts;
    }

  }

  private void exploreCoordinatorSpecific(BlockHeader prev_header, int coord_shard, TreeSet<BlockConcept> concepts)
  {
      BlockSummary prev = node.getForgeInfo().getSummary( prev_header.getSnowHash() );
      if (prev == null) return;

      synchronized(signature_cache)
      {
        signature_cache.put( getSignature(prev), true);
      }

      List<BlockConcept> concept_list = initiateBlockConcepts(prev);
      logger.info("exploreCoordinatorSpecific concepts: " + concept_list.size());

      for(BlockConcept bc : concept_list)
      {
        //System.out.println("exploreCoordinator"+coord_shard+": " + bc.toString());
        // If it is a shard we actually work on
        if (node.getInterestShards().contains(bc.getHeader().getShardId()))
        {
          // If we are splitting into a non-coordinator shard, don't add any other imports
          if (isCoordinator(bc.getHeader().getShardId()))
          {
            // We might be in a different shard from the parent
            int local_coord_shard = bc.getHeader().getShardId();
            // Make a list sorted by block height of things we could possible include
            ListMultimap<Integer, BlockHeader> possible_import_blocks =
                   MultimapBuilder.treeKeys().arrayListValues().build();
            Set<ChainHash> possible_hashes = new HashSet<>();

            for(BlockHeader current_import_head : prev.getImportedShardsMap().values())
            {
              int depth = node.getParams().getMaxShardSkewHeight()*2;
              depth = 3;
              possible_hashes.addAll(
                node.getForgeInfo().climb( new ChainHash(current_import_head.getSnowHash()), -1,
                  depth));
            }

            logger.info("Possible import hashes: " + possible_hashes.size());
            for(ChainHash ch : possible_hashes)
            {
              BlockHeader blk_h = node.getForgeInfo().getHeader(ch);
              if (blk_h != null)
              if (blk_h.getBlockHeight() + node.getParams().getMaxShardSkewHeight()*2 >= prev_header.getBlockHeight())
              {
                // exclude things that import coord blocks that do not match this one
                boolean invalid_coord_import = false;
                if (blk_h.getShardImportMap().containsKey(local_coord_shard))
                {
                  for(ByteString hash : blk_h.getShardImportMap().get(local_coord_shard).getHeightMap().values())
                  {
                    BlockHeader check = node.getForgeInfo().getHeader(new ChainHash(hash));
                    if ((check==null) || (!node.getForgeInfo().isInChain( prev_header, check)))
                    {
                      invalid_coord_import = true;
                    }
                  }
                }
                // TODO a parent of this block might include a different coordinator block
                // so not so sure about the logic here

                if (!invalid_coord_import)
                {
                  possible_import_blocks.put( blk_h.getBlockHeight(), blk_h );
                }
              }
            }
            logger.info("Possible import blocks: " + possible_import_blocks.size());
          
            for(BlockHeader imp_blk : possible_import_blocks.values())
            {
              logger.info(String.format("Checking block for import: s:%d h:%d - %s",
                imp_blk.getShardId(),
                imp_blk.getBlockHeight(),
                new ChainHash(imp_blk.getSnowHash())));

              try(TimeRecordAuto tra = TimeRecord.openAuto("ShardBlockForge.coordBuild"))
              {
                // Not in the cover set from this coordinator
                if (!ShardUtil.getCoverSet(local_coord_shard, node.getParams()).contains(imp_blk.getShardId()))
                {
                  List<BlockHeader> imp_seq = node.getForgeInfo().getImportPath(bc.getShardHeads(), imp_blk);
                  if (dancer.isCompliant(imp_blk))
                  if (imp_seq != null)
                  if (imp_seq.size() == 1)
                  {
                    BlockHeader join_point = node.getForgeInfo().getLatestShard(imp_blk, local_coord_shard);
                    if (node.getForgeInfo().isInChain(prev_header, join_point))
                    {
                      try
                      {
                        bc = bc.importShard(imp_blk);
                      }
                      catch(ValidationException e)
                      {
                        logger.warning("Build validation: " + e);
                      }
                    }
                  }
                }
              }

            }

          }

          considerAdd(concepts, bc);

        }

      }

  }

  private Set<BlockConcept> exploreFromCoordinatorHead(int coord_shard, MetricLog mlog_parent)
    throws ValidationException
  {
    try(MetricLog mlog = new MetricLog(mlog_parent, "exploreFromCoordinatorHead"))
    {
      mlog.set("coord_shard", coord_shard);
      logger.fine("exploreFromCoordinatorHead(" + coord_shard +")");
      TreeSet<BlockConcept> concepts = new TreeSet<>();
      // Start with the highest block for the coordinator shard
      // Then take the set of import blocks <integer,blockhead> and descend from all of those
      // Those are our potential block parents
      // Build concepts from them all.
      // Or just the leafs?

      // Since we start with what we have included, any of these blocks are potentially valid
      // to be included in future coordinators as long as they don't include other coordinator
      // forks

      /*HashSet<ChainHash> coord_heads = new HashSet<>();
      for(BlockHeader bh : node.getForgeInfo().getShardHeads(coord_shard))
      {
        coord_heads.addAll( node.getForgeInfo().getBlocksAround(
          new ChainHash(bh.getSnowHash()), 3, coord_shard));
      }*/

      // TODO - switch to get blocks around
      for(BlockHeader coord_head : node.getForgeInfo().getShardHeads(coord_shard))
      //for(ChainHash coord_hash : coord_heads)
      {
        //BlockHeader coord_head = node.getForgeInfo().getHeader(coord_hash);
        if (coord_head != null)
        {

          logger.fine(String.format("Exploring from coord head: %s s:%d h:%d",
            new ChainHash(coord_head.getSnowHash()).toString(),
            coord_head.getShardId(),
            coord_head.getBlockHeight()));


          // Starting from the more recent coordinator head
          // Find all the imported shard heads

          // Note: the 3x is there because we might be at height X and some other shard is at X-skew-2 or something
          // We can still build a block by bringing in more recent blocks on that shard to bring it to within skew
          Map<Integer, BlockHeader> import_heads = node.getForgeInfo().getImportedShardHeads(
            coord_head, node.getParams().getMaxShardSkewHeight()*3);

          //System.out.println("Import heads:");
          //System.out.println(getSummaryString(import_heads));

          TreeMap<Double, ChainHash> possible_prevs_map = new TreeMap<>();
          HashSet<ChainHash> possible_prevs = new HashSet<>();

          // In case we need to expand into new shard
          possible_prevs.add(new ChainHash(coord_head.getSnowHash()));
          Random rnd = new Random();

          // For each imported shard head, get all the new blocks under each
          for(int src_shard : import_heads.keySet())
          {
            if (node.getInterestShards().contains(src_shard))
            if (!ShardUtil.containsBothChildren(src_shard, import_heads.keySet()))
            {
              ChainHash h = new ChainHash( import_heads.get(src_shard).getSnowHash() );
              Set<ChainHash> set_from_src_shard = node.getForgeInfo().climb(h, -1,
                node.getParams().getMaxShardSkewHeight()*2);

              logger.fine(String.format("Possible prevs from shard %d - %d - %s",
                src_shard, set_from_src_shard.size(), set_from_src_shard));

              possible_prevs.addAll( set_from_src_shard );
              BlockHeader last_header = import_heads.get(src_shard);
              for(ChainHash ch : set_from_src_shard)
              {
                possible_prevs_map.put( rnd.nextDouble() + last_header.getBlockHeight(), ch);
              }

            }
          }
          logger.info("Possible_prevs: " + possible_prevs.size());
          mlog.set("possible_prevs", possible_prevs.size());

          for(ChainHash prev_hash : possible_prevs_map.values())
          {
            expandPrev(import_heads, prev_hash, coord_head, concepts);
            //if (concepts.size() > 20) break;
          }
        }
      }

      mlog.set("count", concepts.size());
      return concepts;
    }
  }

  /**
   * Expand from the given previous block using the selected coordinator head.
   */
  public void expandPrev(Map<Integer, BlockHeader> import_heads, ChainHash prev_hash, BlockHeader coord_head, TreeSet<BlockConcept> concepts)
    throws ValidationException
  {
    //System.out.println("Expanding: " + prev_hash);
    BlockSummary prev = node.getForgeInfo().getSummary( prev_hash );
    if (prev == null)
    {
      logger.warning(String.format("Unable to expand on %s - no summary", prev_hash.toString()));
      if (node.getForgeInfo().getSummary( prev_hash ) == null) System.out.println(" no summary");
      if (node.getForgeInfo().getHeader( prev_hash ) == null) System.out.println(" no header");
      if (node.getShardUtxoImport().getImportBlock( prev_hash ) == null) System.out.println(" no import");
      return;
    }

    //System.out.println("Expanding: " + prev_hash + " A");

    synchronized(signature_cache)
    {
      signature_cache.put( getSignature(prev), true);
    }
    int prev_shard = prev.getHeader().getShardId();
    int prev_height = prev.getHeader().getBlockHeight();

    // In the case that nothing from the current shard has been merged into
    // the coordinator we need to roll backwards until we find one
    int examine_shard = prev_shard;
    while(!import_heads.containsKey(examine_shard))
    {
      examine_shard = ShardUtil.getShardParentId(examine_shard);
    }
    if (import_heads.containsKey(examine_shard))
    {
      // If this block is not in the chain from whatever the import_head has,
      // don't bother with it
      // We get into this state from a climb from a parent shard into a shard that we
      // already have some locked information for
      if (!node.getForgeInfo().isInChain(prev.getHeader(), import_heads.get(examine_shard)))
      {
        return;
      }

      // If there is something newer on this shard already skip it
      if (import_heads.get(examine_shard).getBlockHeight() > prev_height)
      {
        return;
      }
    }
    //System.out.println("Expanding: " + prev_hash + " B");

    List<BlockHeader> coord_imp_lst = node.getForgeInfo().getImportPath(prev, coord_head);
    if (coord_imp_lst == null) { return; }

    List<BlockConcept> concept_list = initiateBlockConcepts(prev);

    for(BlockConcept bc : concept_list)
    {
      //System.out.println("Considering: " + bc);
      expandConcept(import_heads, bc, coord_head, concepts, coord_imp_lst);

    }


  }

  /**
   * Expand a particular concept with a given coordinator head
   */
  public void expandConcept(Map<Integer, BlockHeader> import_heads, BlockConcept bc, BlockHeader coord_head, TreeSet<BlockConcept> concepts, List<BlockHeader> coord_imp_lst)
    throws ValidationException
  {
    int bc_shard = bc.getHeader().getShardId();

    if (!node.getInterestShards().contains(bc_shard)) return;

    // Already have a block of this shard at this height
    if (import_heads.containsKey(bc_shard))
    if (import_heads.get(bc_shard).getBlockHeight() >= bc.getHeader().getBlockHeight())
      return;

    // Add as many as are in compliance
    for(BlockHeader h : coord_imp_lst)
    {
      if (bc == null) return;
      if (!dancer.isCompliant(h))
      {
        return;
      }

      Map<Integer, BlockHeader> cur_imp_heads = node.getForgeInfo().getImportedShardHeads( h, node.getParams().getMaxShardSkewHeight()*2);

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
              return;
            }
          }

          /*if (path == null)
          {
            return; // If we can't import any header, we are out.
          }*/

          if (path != null)
          {
            for(BlockHeader h_imp : path)
            {
              if (!ShardUtil.getCoverSet(bc.getHeader().getShardId(), node.getParams())
                              .contains(h_imp.getShardId()))
              {
                // Import block that isn't in my my coverset
                try
                {
                bc = bc.importShard(h_imp);
                }
                catch(ValidationException e)
                {

                  if(Dancer.isCoordinator(imp_h.getShardId()))
                  {
                    logger.fine("Unable to import coordinator shard: " +e);
                    return;
                  }
                  else
                  {
                    logger.fine("Unable to import shard, discarding concept: " + e);
                  }

                }
              }
            }
          }
        }

      }

    }
    if (bc != null)
    {
      considerAdd(concepts, bc);
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
            for(ChainHash hash : node.getForgeInfo().climb( new ChainHash(this_shard_import.getSnowHash()), -1,
              node.getParams().getMaxShardSkewHeight()*2))
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
        ChainHash import_hash = new ChainHash( import_header.getSnowHash() );

        ImportedBlock ib = node.getShardUtxoImport().getImportBlockForTarget(import_hash, header.getShardId());
        if (ib == null)
        {
          throw new ValidationException("Unable to load imported block for " + import_hash);
        }
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
    public Integer getImportedBlockCount(){return imported_blocks.size(); }
    public int getAdvancesShard(){return advances_shard; }
    public int getHeight(){return getHeader().getBlockHeight(); }
    public Map<Integer, BlockHeader> getShardHeads(){return shard_heads; }
    private BigInteger getSortWork(){return sort_work; }
    private BigInteger getRandomVal(){return rnd_val;}
    public boolean advancesShard()
    {
      return advances_shard > 0;
    }

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

      if (getHeight() < o.getHeight()) return -1;
      if (getHeight() > o.getHeight()) return 1;

      // More imports is better
      if (getImportedBlockCount() > o.getImportedBlockCount()) return -1;
      if (getImportedBlockCount() < o.getImportedBlockCount()) return 1;

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

    TreeSet<ChainHash> import_set = new TreeSet<>();

    for(Map.Entry<Integer, BlockHeader> me : import_map.entrySet())
    {
      if (me.getKey() != h.getShardId())
      {
        import_set.add(new ChainHash(me.getValue().getSnowHash()));
      }
    }
    sb.append(" ");
    sb.append(import_set.toString());

    ByteString bytes = ByteString.copyFrom(sb.toString().getBytes());
    return new ChainHash( DigestUtil.hash(bytes) );

  }

  public ChainHash getSignature(BlockSummary summary)
  {
    return getSignature(summary.getHeader(), summary.getImportedShardsMap());
  }

  public String getSummaryString(Map<Integer, BlockHeader> import_map)
  {
    StringBuilder sb = new StringBuilder();
    for(Map.Entry<Integer, BlockHeader> me : import_map.entrySet())
    {
      BlockHeader h = me.getValue();

      sb.append(" " + new ChainHash(h.getSnowHash()) + " s:" + h.getShardId() +" h:" + h.getBlockHeight());
      sb.append("\n");
    }

    return sb.toString();
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
      MetricLog mlog = getMlog();

      // If no template requests for 5 minutes, don't bother
      if (last_template_request + 300000L < System.currentTimeMillis())
      {
        current_top_concepts = null;
        tickleUserService();
        mlog.set("no_req",1);
        return;
      }


      // Possible blocks to mine
      TreeSet<BlockConcept> possible_set = new TreeSet<>();

      if (node.getBlockIngestor(0).getHead() == null)
      {
        // Let the old thing do the genesis gag
        return;
      }

      int highest_coord = 0;
      for(int coord : node.getForgeInfo().getNetworkActiveShards().keySet())
      {
        if (isCoordinator(coord))
        {
          highest_coord = Math.max(highest_coord, coord);
        }
      }
      mlog.set("active_shards", node.getForgeInfo().getNetworkActiveShards().keySet().toString());

      possible_set.addAll( exploreCoordinator(highest_coord, mlog) );
      //possible_set.addAll( exploreCoordinator(1, mlog) );

      /*for(int src_shard : node.getCurrentBuildingShards())
      {
        if (isCoordinator(src_shard))
        { // Coordinator dance
          possible_set.addAll( exploreCoordinator(src_shard, mlog) );
        }
      }*/
 
      // TODO put back
      possible_set.addAll(exploreFromCoordinatorHead(highest_coord, mlog));
      //possible_set.addAll(exploreFromCoordinatorHead(1, mlog));

      mlog.set("possible_set_size", possible_set.size());
      mlog.set("shards", node.getCurrentBuildingShards().toString());


      logger.info("Possible blocks: " + possible_set.size() + " on " + node.getCurrentBuildingShards());
      int printed = 0;
      ArrayList<BlockConcept> good_concepts = new ArrayList<>();
      for(BlockConcept c : possible_set)
      {
        if (printed < 10)
        {
          logger.info("  Block Concept: " + c.toString());
        }
        printed++;
        good_concepts.add(c);
        if (printed > 80) break;
      }
      current_top_concepts = good_concepts;

      tickleUserService();

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
      if (cur_top.size() != pruned_concepts.size())
      {
        concept_update_thread.wake();

        // Notify miners
        tickleUserService();
      }
    }
    synchronized(signature_cache)
    {
      signature_cache.put(getSignature(bs), true);
    }

  }



}
