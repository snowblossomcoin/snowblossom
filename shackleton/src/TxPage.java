package snowblossom.shackleton;

import snowblossom.lib.*;
import snowblossom.proto.*;
import snowblossom.shackleton.TxLocator.TxLocation;

public class TxPage
{
  public static String render(Shackleton sh, Transaction tx)
    throws Exception
  {
    ChainCache cache = sh.getCache();
    NetworkParams params = sh.getParams();
    TxView tv = TxView.of(tx, cache, true);
    TxLocation loc = sh.getTxLocator().locate(tv);

    StringBuilder sb = new StringBuilder();
    sb.append("<div class='panel'><h1>Transaction");
    if (tv.coinbase) sb.append(" ").append(Html.pill("accent", "coinbase"));
    if (loc.confirmed)
    {
      int conf = sh.getTxLocator().confirmations(loc);
      sb.append(" ").append(Html.pill("ok", Html.num(conf) + " confirmation" + (conf == 1 ? "" : "s")));
    }
    else if (loc.mempool) sb.append(" ").append(Html.pill("warn", "unconfirmed (mempool)"));
    else sb.append(" ").append(Html.pill("bad", "not found in chain"));
    sb.append("</h1>");
    sb.append("<div class='title-hash'>").append(tv.hash.toString()).append(Html.copy(tv.hash.toString())).append("</div>");

    sb.append("<div class='grid2' style='margin-top:14px'><dl class='kv'>");
    if (loc.confirmed)
    {
      if (loc.block_hash != null)
      {
        sb.append(Html.kv("Block", Html.shardBadge(loc.shard) + " " + Html.blockHeightLink(loc.shard, loc.height) + " · " + Html.blockLink(loc.block_hash)));
      }
      else
      {
        sb.append(Html.kv("Block", "height " + Html.num(loc.height)));
      }
      if (loc.timestamp > 0) sb.append(Html.kv("Timestamp", Html.timeFull(loc.timestamp)));
    }
    else if (loc.mempool)
    {
      sb.append(Html.kv("Status", "in mempool, waiting to be mined"));
    }
    else
    {
      sb.append(Html.kv("Status", "unknown – the transaction is not in a block known to this node, nor in its mempool"));
    }
    sb.append(Html.kv("Size", Html.num(tv.size) + " bytes"));
    sb.append(Html.kv("Fee", Html.amount(tv.fee) + " SNOW <span class='muted small'>(" + Html.dec(tv.feePerByte(), 2) + " flakes / byte)</span>"));
    sb.append("</dl><dl class='kv'>");
    if (tv.in_total >= 0) sb.append(Html.kv("Input total", Html.amount(tv.in_total) + " SNOW"));
    sb.append(Html.kv("Output total", Html.amount(tv.out_total) + " SNOW"));
    sb.append(Html.kv("Version", "" + tv.inner.getVersion()));
    sb.append(Html.kv("Signatures", tv.tx.getSignaturesCount() + " <span class='muted small'>on " + tv.inner.getClaimsCount() + " claim(s)</span>"));
    if (tv.inner.getExtra().size() > 0) sb.append(Html.kv("Extra", "<span class='mono'>" + Html.esc(HexUtil.getSafeString(tv.inner.getExtra())) + "</span>"));
    sb.append(Html.kv("Raw", "<a href='/api/tx/" + tv.hash + "'>JSON</a>"));
    sb.append("</dl></div></div>\n");

    sb.append("<div class='panel'><h2>Inputs &amp; outputs</h2>");
    sb.append(txCard(sh, tv, null, true));
    sb.append("</div>");

    // claims
    if (tv.inner.getClaimsCount() > 0)
    {
      sb.append("<div class='panel'><h2>Claims &amp; signatures</h2>");
      sb.append("<p class='muted small'>Each claim reveals the address specification (keys and signature requirements) of an address whose outputs are spent.</p>");
      for (int c = 0; c < tv.inner.getClaimsCount(); c++)
      {
        AddressSpec spec = tv.inner.getClaims(c);
        String addr = AddressUtil.getAddressString(spec, params);
        sb.append("<div class='tx'><div class='head'>Claim ").append(c).append(": ").append(Html.addrLinkFull(addr));
        sb.append("<span class='sp'></span>").append(Html.pill("", spec.getRequiredSigners() + " of " + spec.getSigSpecsCount() + " signatures required")).append("</div>");
        sb.append("<div class='tblwrap'><table class='tbl'><thead><tr><th>#</th><th>Algorithm</th><th>Signed</th><th>Public key</th></tr></thead><tbody>");
        for (int s = 0; s < spec.getSigSpecsCount(); s++)
        {
          SigSpec sig = spec.getSigSpecs(s);
          String algo;
          try
          {
            algo = SignatureUtil.getAlgo(sig.getSignatureType());
          }
          catch (Throwable t)
          {
            algo = "type " + sig.getSignatureType();
          }
          boolean signed = tv.signed_keys.contains(c + ":" + s);
          String pub = HexUtil.getHexString(sig.getPublicKey());
          sb.append("<tr><td>").append(s).append("</td><td>").append(Html.esc(algo)).append("</td><td>")
            .append(signed ? Html.pill("ok", "signed") : Html.pill("", "no")).append("</td><td class='mono small wrap'>")
            .append(pub.length() > 130 ? pub.substring(0, 128) + "… <span class='muted'>(" + sig.getPublicKey().size() + " bytes)</span>" : pub)
            .append("</td></tr>");
        }
        sb.append("</tbody></table></div></div>");
      }
      sb.append("</div>");
    }
    return sb.toString();
  }

  /**
   * Render a transaction as a card with inputs on the left and outputs on the right.
   * @param highlight address whose rows are highlighted (may be null)
   * @param full show everything (for the tx page) vs compact (for block/address pages)
   */
  public static String txCard(Shackleton sh, TxView tv, AddressSpecHash highlight, boolean full)
  {
    NetworkParams params = sh.getParams();
    StringBuilder sb = new StringBuilder();
    sb.append("<div class='tx' id='tx-").append(tv.hash).append("'>");
    if (!full)
    {
      sb.append("<div class='head'>");
      if (tv.coinbase) sb.append(Html.pill("accent", "coinbase")).append(" ");
      sb.append(Html.txLinkFull(tv.hash)).append(Html.copy(tv.hash.toString()));
      sb.append("<span class='sp'></span><span class='muted small'>").append(Html.num(tv.size)).append(" B");
      if (!tv.coinbase) sb.append(" · fee ").append(Html.snow(tv.fee));
      sb.append("</span></div>");
    }

    sb.append("<div class='io'><div class='col'>");
    if (tv.coinbase)
    {
      CoinbaseExtras ex = tv.inner.getCoinbaseExtras();
      sb.append("<div class='row'><span class='l'>Newly generated coins</span><span class='r muted small'>block ").append(Html.num(ex.getBlockHeight()));
      if (ex.getShardId() > 0) sb.append(" · shard ").append(ex.getShardId());
      sb.append("</span></div>");
      if (ex.getRemarks().size() > 0) sb.append("<div class='sub'>remark: <b>").append(Html.esc(HexUtil.getSafeString(ex.getRemarks()))).append("</b></div>");
      if (ex.getMotionsApprovedCount() > 0) sb.append("<div class='sub'>motions approved: ").append(Html.esc(ex.getMotionsApprovedList().toString())).append("</div>");
      if (ex.getMotionsRejectedCount() > 0) sb.append("<div class='sub'>motions rejected: ").append(Html.esc(ex.getMotionsRejectedList().toString())).append("</div>");
    }
    else
    {
      for (TxView.InputView iv : tv.inputs)
      {
        boolean hl = (highlight != null) && highlight.equals(iv.address);
        sb.append("<div class='row").append(hl ? " hl" : "").append("'><span class='l'>").append(Html.addrLink(iv.address, params));
        sb.append(" <a class='muted small' href='/tx/").append(iv.src_tx).append("' title='source transaction output ").append(iv.src_tx).append(":").append(iv.src_idx).append("'>↳ ").append(Html.shortHash(iv.src_tx.toString())).append(":").append(iv.src_idx).append("</a>");
        sb.append("</span><span class='r'>");
        if (iv.value >= 0) sb.append(Html.amount(iv.value));
        else sb.append("<span class='muted'>?</span>");
        sb.append("</span></div>");
      }
      if (tv.inputs.size() == 0) sb.append("<div class='row'><span class='l muted'>no inputs</span></div>");
    }
    sb.append("</div><div class='arrow'>&rarr;</div><div class='col'>");
    int idx = 0;
    for (TransactionOutput o : tv.inner.getOutputsList())
    {
      AddressSpecHash a = new AddressSpecHash(o.getRecipientSpecHash());
      boolean hl = (highlight != null) && highlight.equals(a);
      sb.append("<div class='row").append(hl ? " hl" : "").append("'><span class='l'>").append(Html.addrLink(a, params));
      if (o.getTargetShard() > 0) sb.append(" ").append(Html.shardBadge(o.getTargetShard()));
      sb.append(" <span class='muted small' title='output index'>#").append(idx).append("</span>");
      sb.append("</span><span class='r'>").append(Html.amount(o.getValue())).append("</span></div>");
      if (o.getForBenefitOfSpecHash().size() > 0)
      {
        sb.append("<div class='sub'>for benefit of ").append(Html.addrLink(new AddressSpecHash(o.getForBenefitOfSpecHash()), params)).append("</div>");
      }
      if (o.hasIds())
      {
        if (o.getIds().getUsername().size() > 0)
        {
          String name = HexUtil.getSafeString(o.getIds().getUsername());
          sb.append("<div class='sub'>username: <a href='/search?q=").append(Html.url(name)).append("'>").append(Html.esc(name)).append("</a></div>");
        }
        if (o.getIds().getChannelname().size() > 0)
        {
          String name = HexUtil.getSafeString(o.getIds().getChannelname());
          sb.append("<div class='sub'>channel: <a href='/search?q=").append(Html.url(name)).append("'>").append(Html.esc(name)).append("</a></div>");
        }
      }
      if (o.hasRequirements())
      {
        if (o.getRequirements().getRequiredBlockHeight() > 0) sb.append("<div class='sub'>spendable from block height ").append(Html.num(o.getRequirements().getRequiredBlockHeight())).append("</div>");
        if (o.getRequirements().getRequiredTime() > 0) sb.append("<div class='sub'>spendable from ").append(Html.date(o.getRequirements().getRequiredTime())).append("</div>");
      }
      idx++;
    }
    sb.append("</div></div>");

    sb.append("<div class='foot'>");
    if (!tv.coinbase)
    {
      sb.append("<span>Fee: <b>").append(Html.snow(tv.fee)).append("</b> SNOW (").append(Html.dec(tv.feePerByte(), 2)).append(" flakes/B)</span>");
      if (tv.in_total >= 0) sb.append("<span>In: ").append(Html.snow(tv.in_total)).append("</span>");
    }
    sb.append("<span>Out: ").append(Html.snow(tv.out_total)).append("</span>");
    if (tv.inner.getExtra().size() > 0) sb.append("<span>Extra: <span class='mono'>").append(Html.esc(HexUtil.getSafeString(tv.inner.getExtra()))).append("</span></span>");
    if (!tv.coinbase)
    {
      sb.append("<span>").append(tv.tx.getSignaturesCount()).append(" signature(s), ").append(tv.inner.getClaimsCount()).append(" claim(s)</span>");
      if (!full)
      {
        for (int c = 0; c < tv.inner.getClaimsCount(); c++)
        {
          AddressSpec spec = tv.inner.getClaims(c);
          String summary;
          try
          {
            summary = AddressUtil.getAddressSpecTypeSummary(spec);
          }
          catch (Throwable t)
          {
            summary = "?";
          }
          sb.append("<span class='small'>claim ").append(c).append(": ").append(Html.esc(summary)).append("</span>");
        }
      }
    }
    sb.append("</div></div>\n");
    return sb.toString();
  }
}
