package ron;

import io.ProgressTracker;
import io.Tsv;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class GenerateInverseDocs {
  public static void main(String[]args)throws Exception {
    String basedir = "/Users/ry23/Dropbox/cmu-sfdc/ron_mallet/";

    String changelists_tsv = basedir+"changelists.txt";
    String failures_tsv = basedir+"brokenby.txt";
    String outfile = basedir + "inverse_docs.txt.gz";
    Tsv brokenby = new Tsv(failures_tsv);
    Tsv changelists = new Tsv(changelists_tsv);
    Map<Integer, String[]> changelist_to_sourcefiles = new HashMap<>();
    for (int i=0; i<changelists.size(); i++) {
      String[] row = changelists.getRow(i);
      int cl_id = Integer.parseInt(row[0]);
      String[] files = row[2].split("\n");
      changelist_to_sourcefiles.put(cl_id, files);
    }
    Multimap<Integer, Integer> test_id_to_changelists = ArrayListMultimap.create();
    for (String[] row : brokenby.rows()) {
      int test_id = Integer.parseInt(row[0]);
      for (String cl : row[1].split("\\|")) {
        int cl_id = Integer.parseInt(cl);
        test_id_to_changelists.put(test_id, cl_id);
      }
    }
    try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(outfile))));
        ProgressTracker pt = new ProgressTracker(null, "write", -1, "test failures", "source files")) {

      for (Map.Entry<Integer, Collection<Integer>> e : test_id_to_changelists.asMap().entrySet()) {
        int test_id = e.getKey();
        out.write(Integer.toString(test_id));
        int x = 0;
        for (int cl_id : e.getValue()) {
          for (String source_file : changelist_to_sourcefiles.get(cl_id)) {
            out.write('\t');
            out.write(source_file);
            x++;
          }
        }
        out.write('\n');;
        pt.advise(1, x);
      }
    }
  }
}
