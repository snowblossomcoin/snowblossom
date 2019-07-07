package snowblossom.lib;

import com.google.common.collect.ImmutableList;
import java.util.LinkedList;
import java.util.Random;
import java.util.List;

public class NonsenseWordList
{
  private static ImmutableList<String> word_list;


  /** Could probably use a resource file or something but that will be build problems
   * so better to just put it in code.
   */
  public static synchronized ImmutableList<String> getWordList()
  {
    if (word_list == null)
    {
      LinkedList<String> words = new LinkedList<>();

      words.add("zogbe");
      words.add("prodslung");
      words.add("lentam");
      words.add("florbia");
      words.add("plean");
      words.add("trongle");
      words.add("lumfig");
      words.add("oblent");
      words.add("drelmag");
      words.add("prompi");
      words.add("rentis");
      words.add("frentam");
      words.add("drumpel");
      words.add("samplar");
      words.add("glod");
      words.add("frengle");
      words.add("crarck");
      words.add("plock");
      words.add("bolup");
      words.add("wrentle");
      words.add("frimel");
      words.add("glorble");
      words.add("snorgfüg");
      words.add("pugster");
      words.add("mork");
      words.add("plork");
      words.add("strog");
      words.add("pagos");
      words.add("pranchy");
      words.add("pogle");
      words.add("grobe");
      words.add("nipe");
      words.add("griple");
      words.add("wroque");
      words.add("trudle");
      words.add("vrain");
      words.add("swashrokie");
      words.add("warble");
      words.add("fwumble");
      words.add("priggle");
      words.add("snord");
      words.add("afing");
      words.add("oplark");
      words.add("dorclic");
      words.add("phobolik");
      words.add("yotaful");
      words.add("elfa");
      words.add("dravo");
      words.add("farlie");
      words.add("melta");
      words.add("eiko");
      words.add("boxrot");
      words.add("jolf");
      words.add("shelo");
      words.add("mindia");
      words.add("mancy");

      word_list = ImmutableList.copyOf(words);

    }

    return word_list;

  }

  public static String getNonsense(int count)
  {
    Random rnd = new Random();
    List<String> w = getWordList();

    StringBuilder sb = new StringBuilder();
    for(int i=0;i<count; i++)
    {
      if (i>0) sb.append(" ");
      sb.append(w.get( rnd.nextInt(w.size()) ));

    }
    return sb.toString();


  }

}
