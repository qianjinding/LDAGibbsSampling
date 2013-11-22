package data;

import io.Tsv;
import io.Tsv.ConflictResolver;
import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import data.PackUtils;

public class MergeDataFilesMain {

  public static void main(String[]args)throws Exception {
    for (String basedir : new String[]{
        "/Users/ry23/Dropbox/cmu-sfdc/ron_mallet/"
    }) {
      File[] datedirs = new File(basedir).listFiles(new FilenameFilter() {
        @Override public boolean accept(File dir, String name) {
          return name.startsWith("2013");
        }
      });
      String[] subdirs = new String[datedirs.length];
      for (int i=0; i<subdirs.length; i++) {
        subdirs[i] = datedirs[i].getName() + "/";
      }
      String[] files = { "changelists.txt", "runs.txt", "test_failures.txt", "test_details.txt", "test_results.json" };
      Map<String, ConflictResolver> resolvers = new HashMap<>();
      resolvers.put("runs.txt", new RunsConflictResolver());
      resolvers.put("test_failures.txt", new FailuresConflictResolver());
      for (String file : files) {
        if (file.endsWith(".txt")) {
          mergeTsv(basedir, subdirs, resolvers, file);
        } else if (file.endsWith(".json")) {
          mergeJson(basedir, subdirs, file);
        } else {
          throw new AssertionError(file);
        }
      }
    }
  }

  private static void mergeJson(String basedir, String[] subdirs, String file) throws IOException,
      JsonParseException, JsonMappingException, FileNotFoundException {
    Map<Integer, Map<Byte, Set<Integer>>> merged = null;
    for (String subdir : subdirs) {
      String filename = basedir + subdir + file;
      if (!new File(filename).exists()) continue;
      Map<Integer, Map<Byte, Set<Integer>>> data = PackUtils.readPackedJson(filename);
      if (merged == null) {
        merged = data;
      } else {
        union(merged, data);
      }
    }
    if (merged == null) {
      System.out.println("no data files found for " + file);
    } else {
      PackUtils.writePackedJson(basedir + file, merged);
    }
  }

  private static void mergeTsv(String basedir, String[] subdirs, Map<String, ConflictResolver> resolvers,
      String file) throws IOException {
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
    } else {
      Collections.sort(merged.rows(), new Comparator<String[]>() {
        @Override public int compare(String[] o1, String[] o2) {
          return o1[0].compareTo(o2[0]);
        }
      });
      merged.save(basedir + file);
    }
  }

  public static final byte PASS = 1;
  public static final byte FAIL = 2;
  public static final byte BREAK = 3;

  private static void union(Map<Integer, Map<Byte, Set<Integer>>> merged,
      Map<Integer, Map<Byte, Set<Integer>>> data) {
    for (Entry<Integer, Map<Byte, Set<Integer>>> e : data.entrySet()) {
      Map<Byte, Set<Integer>> prev = merged.get(e.getKey());
      if (prev != null) {
        // one of the runs may have more test results than the other
        cmpfail: {
        cmp: {
        if (prev.equals(e.getValue()))
          break cmpfail;
        int a = Integer.compare(prev.get(PASS).size(), e.getValue().get(PASS).size());
        int b = Integer.compare(prev.get(FAIL).size(), e.getValue().get(FAIL).size());
        int c = Integer.compare(prev.get(BREAK).size(), e.getValue().get(BREAK).size());
        if (a == 0) {
          if (!prev.get(PASS).equals(e.getValue().get(PASS)))
            break cmp;
        } else {
          if ((a<0 && (b>0 || c>0)) || a>0 && (b<0 || c<0))
            break cmp;
          SetView<Integer> i = Sets.intersection(prev.get(PASS), e.getValue().get(PASS));
          if (!(i.equals(prev.get(PASS)) ^ i.equals(e.getValue().get(PASS))))
            break cmp;
        }
        if (b == 0) {
          if (!prev.get(FAIL).equals(e.getValue().get(FAIL)))
            break cmp;
        } else {
          if ((b<0 && (a>0 || c>0)) || b>0 && (a<0 || c<0))
            break cmp;
          SetView<Integer> i = Sets.intersection(prev.get(FAIL), e.getValue().get(FAIL));
          if (!(i.equals(prev.get(FAIL)) ^ i.equals(e.getValue().get(FAIL))))
            break cmp;
        }
        if (c == 0) {
          if (!prev.get(BREAK).equals(e.getValue().get(BREAK)))
            break cmp;
        } else {
          if ((c<0 && (b>0 || a>0)) || c>0 && (b<0 || a<0))
            break cmp;
          SetView<Integer> i = Sets.intersection(prev.get(BREAK), e.getValue().get(BREAK));
          if (!(i.equals(prev.get(BREAK)) ^ i.equals(e.getValue().get(BREAK))))
            break cmp;
        }
        break cmpfail;
      }
      throw new RuntimeException("too fancy");
      }
      // otherwise, same data under the same key, no op
      } else {
        merged.put(e.getKey(), e.getValue());
      }
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


  private static final class FailuresConflictResolver implements ConflictResolver {
    @Override public String[] resolve(String[] cols, String[] row1, String[] row2) {
      int cmp = row1[row1.length-1].compareTo(row2[row2.length-1]);
      if (cmp < 0) {
        return row2;
      } else if (cmp > 0) {
        return row1;
      }
      throw new RuntimeException("Too fancy for me, I just wanted to return the more recent row according to RUN_ID:\n"
          + Arrays.toString(row1)
          +"\n"
          + Arrays.toString(row2));
    }
  }

  private static final class RunsConflictResolver implements ConflictResolver {
    @Override public String[] resolve(String[] cols, String[] row1, String[] row2) {
      // replace a waiting run with a non-waiting run.
      int idxStatus = find(cols, "STATUS");
      if (idxStatus == -1) throw new RuntimeException("Couldn't find the STATUS column: " + Arrays.toString(cols));
      int idxType = find(cols, "TYPE");
      if (idxType == -1) throw new RuntimeException("Couldn't find the TYPE column: " + Arrays.toString(cols));
      int idxBuildFailed = find(cols, "BUILD_FAILED");
      if (idxBuildFailed == -1) throw new RuntimeException("Couldn't find the BUILD_FAILED column: " + Arrays.toString(cols));

      boolean partial1 = "PARTIAL".equals(row1[idxType]);
      boolean partial2 = "PARTIAL".equals(row2[idxType]);

      boolean full1 = "FULL".equals(row1[idxType]);
      boolean full2 = "FULL".equals(row2[idxType]);

      boolean skipped1 = "SKIP".equals(row1[idxStatus]);
      boolean skipped2 = "SKIP".equals(row2[idxStatus]);

      boolean buildFailed1 = "y".equals(row1[idxBuildFailed]);
      boolean buildFailed2 = "y".equals(row2[idxBuildFailed]);


      // [36001141, 14-NOV-13, FINISHED, 8398687, PARTIAL, n]
      // [36001141, 14-NOV-13, SKIP, 8398687, FULL, y]
      if (skipped1 != skipped2) {
        if (skipped1) return row1;
        return row2;
      }

      if (skipped1 && skipped2) {
        // [35989619, 14-NOV-13, SKIP, 8398254, PARTIAL, y]
        // [35989619, 14-NOV-13, SKIP, 8398254, FULL, y]
        if (partial1 != partial2 && full1 != full2 && buildFailed1 && buildFailed2) {
          if (partial1 && full2) {
            return row1;
          }
          assert full1 && partial2;
          return row2;
        }
      }

      boolean wait1 = "WAIT".equals(row1[idxStatus]);
      boolean wait2 = "WAIT".equals(row2[idxStatus]);
      if (wait1 != wait2) {
        if (wait1) { // !wait2
          return row2;
        }
        // !wait1 && wait2
        return row1;
      }

      boolean reserved1 = "RESERVED".equals(row1[idxStatus]);
      boolean reserved2 = "RESERVED".equals(row2[idxStatus]);
      if (reserved1 != reserved2) {
        if (reserved1) { // !reserved2
          return row2;
        }
        // !reserved1 && reserved2
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

      // replace null type and null build_failed with non-null type and non-null build_failed
      boolean nullBuildFailed1 = "".equals(row1[idxBuildFailed]);
      boolean nullBuildFailed2 = "".equals(row2[idxBuildFailed]);

      nullboth:
        if (nullBuildFailed1 != nullBuildFailed2) {
          if (nulltype1 == nullBuildFailed1 && nulltype2 == nullBuildFailed2) {
            // test that every other field is the same and that the only difference is the presence/absence
            // of the type and build_failed columns
            for (int i=0; i<cols.length; i++) {
              if (i == idxType) continue;
              if (i == idxBuildFailed) continue;
              if (!row1[i].equals(row2[i])) {
                break nullboth;
              }
            }
            // use the row that has the value for the TYPE and BUILD_FAILED columns
            if (!nulltype1) return row1;
            return row2;
          }
        }

      throw new RuntimeException(
          "Too fancy for me to resolve:\n"
              + Arrays.toString(cols)+"\n"
              + Arrays.toString(row1)+"\n"
              + Arrays.toString(row2));
    }
  }

}
