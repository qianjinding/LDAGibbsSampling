package data;

import io.TsvParser;
import java.util.*;
import java.util.Map.Entry;
import data.ChangelistSourceFiles.Changelist;

public class Utils {
  private Utils() {}

  /**
   * merges a hashtable of counts into a larger hashtable that has many hashtables of counts.
   *
   * For example, the hashmap m may contain a mapping from file names to hashmaps containing
   * words and their counts.
   */
  public static void increment(Map<String, Map<String, Integer>> m, String key, Map<String, Integer> values) {
    Map<String, Integer> prev = m.get(key);
    if (prev == null) {
      m.put(key, prev = new HashMap<>());
    }
    for (Entry<String, Integer> e : values.entrySet()) {
      Integer oldcount = prev.get(e.getKey());
      prev.put(e.getKey(), (oldcount == null ? 0 : oldcount.intValue()) + e.getValue());
    }
   }


  /**
   * Figures out the new failures
   */
  public static Map<String, Map<String, Integer>> get_new_failures_by_changelist(ChangelistSourceFiles cls,
      TsvParser runs, TestFailuresByRun test_failures_by_run_id) {
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
      return changelist_to_failures;
    }


}
