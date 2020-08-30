package snowblossom.client;

import snowblossom.util.proto.Offer;
import snowblossom.util.proto.OfferAcceptance;

public interface OfferPayInterface
{
  public void maybePayOffer(Offer offer, OfferAcceptance oa);

}
