package snowblossom.shackleton;

import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.ChainHash;
import snowblossom.lib.TransactionUtil;
import snowblossom.proto.*;

import java.io.PrintStream;
import java.text.DecimalFormat;

public class VotingPage
{
  private final PrintStream out;
  private final int motion, lookback;
  private final Shackleton shackleton;

  public VotingPage(PrintStream out, Shackleton shackleton, int motion, int lookback)
  {
    this.out = out;
    this.shackleton = shackleton;
    this.motion = motion;
    this.lookback = lookback;
    if (lookback <= 0) lookback = 1000;
  }


  private int yes, no, oldestVote = -1, maxHeight;
  public void render()
  {
    if (lookback > 2000)
    {
      paragraph("cannot look back " + lookback + " blocks.  (2000 max)");
      return;
    }

    paragraph("Motion: " + motion);
    paragraph("Total Blocks Checked: " + lookback);

    doCount();

    paragraph("Yes: " + yes);
    paragraph("No: " + no);
    paragraph("Oldest Vote: " + oldestVote + " (" + (maxHeight - oldestVote) + " blocks ago)");

  }

  private void paragraph(String s)
  {
    out.println("<p>" + s + "</p>");
  }

  private void doCount()
  {
    NodeStatus nodeStatus = shackleton.getStub().getNodeStatus(NullRequest.newBuilder().build());
    int height = nodeStatus.getHeadSummary().getHeader().getBlockHeight();
    maxHeight = height;
    for(int h= height; h>= Math.max(0, height - lookback); h--)
    {
      BlockHeader header = shackleton.getStub().getBlockHeader(RequestBlockHeader.newBuilder().setBlockHeight(h).build());
      Block block = shackleton.getStub().getBlock(RequestBlock.newBuilder().setBlockHash(header.getSnowHash()).build());
      tallySingleBlock(block);
    }
  }

  private void tallySingleBlock(Block block)
  {
    for(Transaction tx : block.getTransactionsList())
    {
      TransactionInner inner = TransactionUtil.getInner(tx);
      if (inner != null && inner.getIsCoinbase() && inner.hasCoinbaseExtras())
      {
        CoinbaseExtras extras = inner.getCoinbaseExtras();
        if (extras != null && extras.getMotionsApprovedCount() > 0)
        {
          for (int p : extras.getMotionsApprovedList())
          {
            if (p == motion)
            {
              yes++;
              oldestVote = block.getHeader().getBlockHeight();
              return;
            }
          }
        }
        if (extras != null && extras.getMotionsRejectedCount() > 0)
        {
          for (int p : extras.getMotionsRejectedList())
          {
            if (p == motion)
            {
              no++;
              oldestVote = block.getHeader().getBlockHeight();
              return;
            }
          }
        }
        break;
      }
    }
  }
}
