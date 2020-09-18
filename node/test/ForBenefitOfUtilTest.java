package node.test;

import org.junit.Assert;
import org.junit.Test;
import snowblossom.node.ForBenefitOfUtil;

public class ForBenefitOfUtilTest
{
  @Test
  public void testBasic()
  {
    Assert.assertEquals(ForBenefitOfUtil.normalize("Fräd"), ForBenefitOfUtil.normalize("frad"));
    Assert.assertEquals(ForBenefitOfUtil.normalize("FRAD"), ForBenefitOfUtil.normalize("frad"));
    Assert.assertEquals("some ape", ForBenefitOfUtil.normalize("🦧"), ForBenefitOfUtil.normalize("🦧"));
    Assert.assertEquals("fireduck", ForBenefitOfUtil.normalize("fireduck"), ForBenefitOfUtil.normalize("𝓕ire𝐃uc𝐤"));
    Assert.assertEquals("fireduck1", ForBenefitOfUtil.normalize("fireduck1"), ForBenefitOfUtil.normalize("fireduck𝟏"));

    Assert.assertNotEquals("different hearts", ForBenefitOfUtil.normalize("♥"), ForBenefitOfUtil.normalize("💚"));
    Assert.assertNotEquals("Brown v. Board of Education", ForBenefitOfUtil.normalize("🧑🏾"),ForBenefitOfUtil.normalize("🧑"));
    Assert.assertNotEquals("fireduck_", ForBenefitOfUtil.normalize("fireduck-"), ForBenefitOfUtil.normalize("fireduck_"));
    Assert.assertNotEquals("fire.duck", ForBenefitOfUtil.normalize("fireduck"), ForBenefitOfUtil.normalize("fire.duck"));
    Assert.assertNotEquals("fire♥duck", ForBenefitOfUtil.normalize("fireduck"), ForBenefitOfUtil.normalize("fire♥duck"));
    Assert.assertNotEquals("fireduck1", ForBenefitOfUtil.normalize("fireduck1"), ForBenefitOfUtil.normalize("fireduck➊"));
    Assert.assertNotEquals("火鸭", ForBenefitOfUtil.normalize("火鸭"), ForBenefitOfUtil.normalize("鸭火"));
    Assert.assertNotEquals("simplified vs traditional", ForBenefitOfUtil.normalize("火鸭"), ForBenefitOfUtil.normalize("火鴨"));


  }


}
