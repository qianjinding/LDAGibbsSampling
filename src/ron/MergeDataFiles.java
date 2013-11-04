package ron;

import io.Tsv;
import io.Tsv.ConflictResolver;
import java.io.File;
import java.util.*;

public class MergeDataFiles {
  public static void main(String[]args)throws Exception {
    String basedir = "/Users/ry23/Dropbox/cmu-sfdc/ron_mallet/";
    String[] subdirs = {"20130927/", "20131101/", "20131104/" };
    String[] files = { "changelists.txt", "runs.txt", "test_failures.txt" };
    Map<String, ConflictResolver> resolvers = new HashMap<>();
    resolvers.put("runs.txt", new ConflictResolver() {
      @Override public String[] resolve(String[] cols, String[] row1, String[] row2) {
        // replace a waiting run with a non-waiting run.
        int idxStatus = find(cols, "STATUS");
        if (idxStatus == -1) throw new RuntimeException("Couldn't find the STATUS column: " + Arrays.toString(cols));

        boolean wait1 = "WAIT".equals(row1[idxStatus]);
        boolean wait2 = "WAIT".equals(row2[idxStatus]);
        if (wait1 != wait2) {
          if (wait1) { // !wait2
            return row2;
          }
          // !wait1 && wait2
          return row1;
        }

        // If one run is RUNNING and the other is not, let's use the other row
        boolean running1 = "RUNNING".equals(row1[idxStatus]);
        boolean running2 = "RUNNING".equals(row2[idxStatus]);
        if (running1 != running2) {
          if (running1) return row2;
          return row1;
        }


        // replace null run type with non-null
        int idxType = find(cols, "TYPE");
        if (idxType == -1) throw new RuntimeException("Couldn't find the TYPE column: " + Arrays.toString(cols));

        boolean nulltype1 = "".equals(row1[idxType]);
        boolean nulltype2 = "".equals(row2[idxType]);
        nullblock:
          if (nulltype1 != nulltype2) {
          // test that every other field is the same and that the only difference is the presence/absence
          // of the type columns
          for (int i=0; i<cols.length; i++) {
            if (i == idxType) continue;
            if (i == idxStatus) continue; // it's ok for the run status to change
            if (!row1[i].equals(row2[i])) {
              break nullblock;
            }
          }
          // use the row that has the value for the TYPE column
          if (!nulltype1) return row1;
          return row2;
        }

        throw new RuntimeException(
            "Too fancy for me to resolve:\n"
            + Arrays.toString(cols)+"\n"
            + Arrays.toString(row1)+"\n"
            + Arrays.toString(row2));
      }

    });
    for (String file : files) {
      Tsv merged = null;
      for (String subdir : subdirs) {
        String filename = basedir + subdir + file;
        if (!new File(filename).exists()) continue;
        Tsv tsv = new Tsv(filename);
        if (merged == null) {
          merged = tsv;
          continue;
        }
        merged.union(tsv, resolvers.get(file));
      }
      if (merged == null) {
        System.out.println("no data files found for " + file);
        continue;
      }
      Collections.sort(merged.rows(), new Comparator<String[]>() {
        @Override public int compare(String[] o1, String[] o2) {
          return o1[0].compareTo(o2[0]);
        }
      });
      merged.save(basedir + file);
    }
  }

  private static int find(String[] haystack, String needle) {
    for (int i=0; i<haystack.length; i++) {
      if (needle.equals(haystack[i])) {
        return i;
      }
    }
    return -1;
  }

}
