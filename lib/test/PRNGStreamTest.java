package lib.test;

import com.google.protobuf.ByteString;
import org.apache.commons.codec.binary.Hex;
import org.junit.Assert;
import org.junit.Test;
import snowblossom.lib.PRNGStream;

public class PRNGStreamTest
{
  @Test
  public void testSeedStable()
  {
    PRNGStream a = new PRNGStream("a");
    PRNGStream b = new PRNGStream("a");

    byte[] a_bytes = new byte[1048576];
    byte[] b_bytes = new byte[1048576];

    a.nextBytes(a_bytes);
    b.nextBytes(b_bytes);

    ByteString a_str = ByteString.copyFrom(a_bytes);
    ByteString b_str = ByteString.copyFrom(b_bytes);
    
    Assert.assertArrayEquals(a_bytes, b_bytes);
    Assert.assertEquals(a_str, b_str);

  }

  @Test
  public void testSeedDiff()
  {
    PRNGStream a = new PRNGStream("a");
    PRNGStream b = new PRNGStream("b");

    byte[] a_bytes = new byte[1048576];
    byte[] b_bytes = new byte[1048576];

    a.nextBytes(a_bytes);
    b.nextBytes(b_bytes);

    assertArrayNotEq(a_bytes, b_bytes);

  }

  @Test
  public void testMixMixDiff()
  {
    PRNGStream a = new PRNGStream("aaa");
    PRNGStream b = new PRNGStream("aaa");

    byte[] a_bytes = new byte[1048576];
    byte[] b_bytes = new byte[1048576];

    a.nextBytes(a_bytes);
    b.nextBytes(b_bytes);

    Assert.assertArrayEquals(a_bytes, b_bytes);

    byte[] mix_a = new byte[2];
    byte[] mix_b = new byte[2];

    mix_a[0]=8;
    mix_b[0]=7;

    a.mixBytes(mix_a);
    b.mixBytes(mix_b);


    a.nextBytes(a_bytes);
    b.nextBytes(b_bytes);
    assertArrayNotEq(a_bytes, b_bytes);

  }

  @Test
  public void testMixStable()
  {
    PRNGStream a = new PRNGStream("aaa");
    PRNGStream b = new PRNGStream("aaa");

    byte[] a_bytes = new byte[1048576];
    byte[] b_bytes = new byte[1048576];

    a.nextBytes(a_bytes);
    b.nextBytes(b_bytes);

    Assert.assertArrayEquals(a_bytes, b_bytes);

    byte[] mix_a = new byte[2];
    byte[] mix_b = new byte[2];

    mix_a[0]=8;
    mix_b[0]=8;

    a.mixBytes(mix_a);
    b.mixBytes(mix_b);


    a.nextBytes(a_bytes);
    b.nextBytes(b_bytes);
    
    Assert.assertArrayEquals(a_bytes, b_bytes);

  }

  @Test
  public void testMixPreDiff()
  {
    PRNGStream a = new PRNGStream("aaa");
    PRNGStream b = new PRNGStream("bbb");

    byte[] a_bytes = new byte[1048576];
    byte[] b_bytes = new byte[1048576];

    a.nextBytes(a_bytes);
    b.nextBytes(b_bytes);

    assertArrayNotEq(a_bytes, b_bytes);

    byte[] mix_a = new byte[4096];
    byte[] mix_b = new byte[4096];

    mix_a[0]=8;
    mix_b[0]=8;

    a.mixBytes(mix_a);
    b.mixBytes(mix_b);

    a.nextBytes(a_bytes);
    b.nextBytes(b_bytes);
    assertArrayNotEq(a_bytes, b_bytes);

  }

  @Test
  public void testStable()
    throws Exception
  {
    PRNGStream a = new PRNGStream("aaa");
    byte[] a_bytes = new byte[64];

    a.nextBytes(a_bytes);

    byte[] expected = Hex.decodeHex("7dfffb85fe1685f7a131141ec41838a01695f6d24d22bd577bc31edc7dcdb6706eb36474b9e714800377658813f881066f800aabf9ca558094bf901061004cc2");

    Assert.assertEquals(Hex.encodeHexString(expected), Hex.encodeHexString(a_bytes));
  }

  @Test
  public void testStableAfterMix()
    throws Exception
  {
    PRNGStream a = new PRNGStream("aaa");

    byte[] mix = new byte[8];
    mix[0]=0;
    mix[5]=91;
    a.mixBytes(mix);

    byte[] a_bytes = new byte[64];

    a.nextBytes(a_bytes);

    byte[] expected = Hex.decodeHex("a83bfaf92672148b614a7471d36f97982e03b50862f923d2ed9d9bf1e07a24a8c9bbb10ebba47d87ebfe0aa36d65d3846b25f71f6e8e48c7266eae4b6fb7312a");

    Assert.assertEquals(Hex.encodeHexString(expected), Hex.encodeHexString(a_bytes));
  }


  private void assertArrayNotEq(byte[] a, byte[] b)
  {

    ByteString a_str = ByteString.copyFrom(a);
    ByteString b_str = ByteString.copyFrom(b);

    Assert.assertFalse(a_str.equals(b_str));
  }

}
