package ron;

import io.Tsv;
import java.io.File;
import java.io.IOException;
import java.util.List;
import com.google.common.base.Charsets;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import com.google.common.io.Files;

/**
 * Taking fixedby.txt, brokenby.txt, and changelists.txt, formulate
 * a single dataset
 */
public class MapToDocuments {
  public static void main(String[]args)throws Exception {
    String basedir = "/Users/ry23/Dropbox/cmu-sfdc/ron_mallet/";
    Multimap<Integer, Integer> brokenby = read(basedir + "brokenby.txt");
    Multimap<Integer, Integer> fixedby = read(basedir + "fixedby.txt");
    Tsv changelists = new Tsv(basedir + "changelists.txt");
  }

  private static Multimap<Integer, Integer> read(String string) throws IOException {
    Multimap<Integer, Integer> ret = TreeMultimap.create();
    List<String> lines = Files.readLines(new File(string), Charsets.US_ASCII);
    // skip header
    for (int x = 1; x < lines.size(); x++) {
      String[] ar = lines.get(x).split("\t");
      for (String changelist_id : ar[1].split("\\|")) {
        int test_id = Integer.parseInt(ar[0]);
        ret.put(test_id, Integer.parseInt(changelist_id));
      }
    }
    return ret;
  }
}
