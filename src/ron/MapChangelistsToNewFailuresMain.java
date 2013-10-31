package ron;

import io.ProgressTracker;
import io.TsvParser;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.*;
import data.*;
import data.ChangelistSourceFiles.Changelist;

public class MapChangelistsToNewFailuresMain {
  public static void main(String[]args)throws Exception {
    String changelists_tsv = "/Users/ry23/Dropbox/cmu-sfdc/data/changelists.txt";
    String runs_tsv = "/Users/ry23/Dropbox/cmu-sfdc/data/runs.txt";
    String test_failures_tsv = "/Users/ry23/Dropbox/cmu-sfdc/data/test_failures.txt";

    ChangelistSourceFiles cls = ChangelistSourceFiles.readChangelistToFileMapping(changelists_tsv);

    // ID CREATE_DATE STATUS CHANGELIST TYPE BUILD_FAILED
    // 1234 18-SEP-13 SKIP|FINISHED 54321 PARTIAL|FULL n|y
    TsvParser runs = new TsvParser(runs_tsv);

    TestFailuresByRun test_failures_by_run_id = TestFailuresByRun.readTestFailures(test_failures_tsv);

    // for each run, create pointer back to most recent full run that
    // finished and did not fail
    int ptr = -1;
    int[] prev_full_run_idxs = new int[runs.size()];
    int[] next_full_run_idxs = new int[runs.size()];
    for (int i=0; i<prev_full_run_idxs.length; i++) {
      String[] row = runs.getRow(i);
      prev_full_run_idxs[i] = ptr;
      if ("FINISHED".equals(row[2]) && "FULL".equals(row[4]) && "n".equals(row[5])) {
        ptr = i;
      }
    }
    ptr = -1;
    for (int i=prev_full_run_idxs.length - 1; i>= 0; i--) {
      String[] row = runs.getRow(i);
      if ("FINISHED".equals(row[2]) && "FULL".equals(row[4]) && "n".equals(row[5])) {
        ptr = i;
      }
      next_full_run_idxs[i] = ptr;
    }


    // build index mapping from changelist id's to run id's.  There may be more than
    // one run for each changelist.
    Map<String, TreeSet<Integer>> changelist_id_to_run_idxs = new HashMap<>();
    int index = 0;
    for (String[] run : runs.rows()) {
      TreeSet<Integer> prev = changelist_id_to_run_idxs.get(run[3]);
      if (prev == null) {
        changelist_id_to_run_idxs.put(run[3], prev = new TreeSet<Integer>());
      }
      // if it were the case that a single changelist didn't have multiple
      // preceding or interceding full runs, then this for loop could be
      // uncommented
//      for (int prev_run_idx : prev) {
//        if (prev_full_run_idxs[index] != prev_full_run_idxs[prev_run_idx]) {
//          throw new RuntimeException(index+" " + prev_run_idx+" " + prev_full_run_idxs[index] +" " + prev_full_run_idxs[prev_run_idx]);
//        }
//      }
      prev.add(index);
      index++;
    }


    Map<String, Map<String, Integer>> changelist_to_failures = new HashMap<>();

    int changelists = 0;
    int testfailures = 0;
    int skipped_changelists = 0;
    for (Changelist changelist : cls.changelists()) {
      String changelist_id = changelist.changelist_id;
      // for each changelist, find associated run(s)
      // we want to capture the additional tests that failed in the next
      // full run when compared to the previous full run
      // scooping up all the failures in between
      TreeSet<Integer> run_idxs = changelist_id_to_run_idxs.get(changelist_id);
      if (run_idxs == null) throw new RuntimeException();
      if (run_idxs.isEmpty()) throw new IllegalStateException();
      int first_run = run_idxs.first();
      int last_run = run_idxs.last();
      int prev_full_run_idx = prev_full_run_idxs[first_run];
      int next_full_run_idx = next_full_run_idxs[last_run];
      if (prev_full_run_idx == -1) {
        skipped_changelists++;
        System.err.println(changelist_id+": skipping due to lack of preceding full run");
        continue;
      }
      if (next_full_run_idx == -1) {
        skipped_changelists++;
        System.err.println(changelist_id+": skipping due to lack of succeeding full run");
        continue;
      }
      String prev_full_run_id = runs.getRow(prev_full_run_idx)[0];
      String next_full_run_id = runs.getRow(next_full_run_idx)[0];
      Set<String> starting_failures = test_failures_by_run_id.getTestFailures(prev_full_run_id);
      Set<String> ending_failures = test_failures_by_run_id.getTestFailures(next_full_run_id);
      Set<String> new_failures = new HashSet<>(ending_failures);
      new_failures.removeAll(starting_failures);
      System.err.println(changelist_id+": " + starting_failures.size()+" " + ending_failures.size()+" " + new_failures.size());
      changelists++;
      testfailures += new_failures.size();


      Map<String, Integer> failure_count = new HashMap<>();
      Map<String, Integer> prev = changelist_to_failures.put(changelist_id, failure_count);
      if (prev != null) throw new RuntimeException(changelist_id + " " + prev);

      for (String tf : new_failures) {
        Integer count = failure_count.get(tf);
        if (count == null) {
          failure_count.put(tf, 1);
        } else {
          failure_count.put(tf, count + 1);
        }
      }
    }

    System.err.println("skipped: " + skipped_changelists+" handled: " + changelists+", failures: " + testfailures);

    try (BufferedWriter out = new BufferedWriter(new FileWriter("changelist_to_failures_doc.txt"));
        ProgressTracker pt = new ProgressTracker(null, "write", -1, "documents", "words", "bytes")) {
      for (Map.Entry<String, Map<String, Integer>> e_failure_count : changelist_to_failures.entrySet()) {
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
}
