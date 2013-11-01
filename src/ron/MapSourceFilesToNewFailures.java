package ron;

import io.ProgressTracker;
import io.TsvParser;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.*;
import java.util.Map.Entry;
import data.ChangelistSourceFiles;
import data.TestFailuresByRun;

public class MapSourceFilesToNewFailures {
  public static void main(String[]args)throws Exception {
    String changelists_tsv = "/Users/ry23/Dropbox/cmu-sfdc/data/changelists.txt";
    String runs_tsv = "/Users/ry23/Dropbox/cmu-sfdc/data/runs.txt";
    String test_failures_tsv = "/Users/ry23/Dropbox/cmu-sfdc/data/test_failures.txt";

    ChangelistSourceFiles cls = ChangelistSourceFiles.readChangelistToFileMapping(changelists_tsv);

    // ID CREATE_DATE STATUS CHANGELIST TYPE BUILD_FAILED
    // 1234 18-SEP-13 SKIP|FINISHED 54321 PARTIAL|FULL n|y
    TsvParser runs = new TsvParser(runs_tsv);

    TestFailuresByRun test_failures_by_run_id = TestFailuresByRun.readTestFailures(test_failures_tsv);

    Map<String, Map<String, Integer>> changelist_to_failures = MapChangelistsToNewFailuresMain.get_new_failures_by_changelist(cls, runs,
        test_failures_by_run_id);

    Map<String, Map<String, Integer>> source_file_to_failures = new HashMap<>();
    // additional xform step required to accumulate test failure counts by source file
    for (Map.Entry<String, Map<String, Integer>> e : changelist_to_failures.entrySet()) {
      for (String source_file : cls.getSourceFiles(e.getKey())) {
        increment(source_file_to_failures, source_file, e.getValue());
      }
    }

    try (BufferedWriter out = new BufferedWriter(new FileWriter("docs.txt"));
        ProgressTracker pt = new ProgressTracker(null, "write", -1, "documents", "words", "bytes")) {
      for (Map.Entry<String, Map<String, Integer>> e_failure_count : source_file_to_failures.entrySet()) {
        Map<String, Integer> failure_count = e_failure_count.getValue();
        String src_file_name = e_failure_count.getKey();
        if (src_file_name.contains("\t")) throw new RuntimeException();
        out.write(src_file_name);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : failure_count.entrySet()) {
          for (int i=0; i<e.getValue(); i++) {
            sb.append('\t');
            sb.append(e.getKey());
            pt.advise(0, 1, 0);
          }
        }
        String s = sb.toString();
        out.write(s);
        out.write('\n');
        pt.advise(1, 0, s.length());
      }
    }
  }

  private static void increment(Map<String, Map<String, Integer>> m, String key, Map<String, Integer> values) {
    Map<String, Integer> prev = m.get(key);
    if (prev == null) {
      m.put(key, prev = new HashMap<>());
    }
    for (Entry<String, Integer> e : values.entrySet()) {
      Integer oldcount = prev.get(e.getKey());
      prev.put(e.getKey(), (oldcount == null ? 0 : oldcount.intValue()) + e.getValue());
    }
   }
}
