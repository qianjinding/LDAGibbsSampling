package ron;

import io.LineReader;
import io.ProgressTracker;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.*;
import ron.GenerateBrokenByAndFixedBy.TestSuiteRun.Status;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import data.*;

/**
 * given "test_results.json," this utility produces two output files, "fixedby.txt" and "brokenby.txt"
 * where each line represents a test being caused to fail or caused to succeed by one or more
 * changelist_id's.
 *
 */
public class GenerateBrokenByAndFixedBy {
  //  ID      CREATE_DATE     TEST_DETAIL_ID  RUN_ID  TEST_STATUS
  //  9126993559      31-OCT-13       3233896 35011705        1
  public static void main(String[]args)throws Exception {
    String basedir = "/Users/ry23/Dropbox/cmu-sfdc/ron_mallet/";

    final Runs runs = LineReader.handle(true, new Runs(), basedir + "runs.txt");

    // run_id -> test_status -> test_detail_id
    TestResults results = new TestResults(PackUtils.readPackedJson(basedir + "test_results.json"));

    Set<Integer> testids = new TreeSet<>();

    try (ProgressTracker pt = new ProgressTracker(null, "index", results.size(), "runs", "tests")) {
      for (int i=0; i<results.size(); i++) {
        TestSuiteRun tsr = results.get(i);
        for (int testid : tsr.getTests()) {
          testids.add(testid);
        }
        pt.advise(1, tsr.size());
      }
    }

    try (ProgressTracker pt = new ProgressTracker(null, "save", testids.size(), "tests");
        BufferedWriter brokenby = new BufferedWriter(new FileWriter(basedir + "brokenby.txt"));
        BufferedWriter fixedby = new BufferedWriter(new FileWriter(basedir + "fixedby.txt"))) {
      String header = "TEST_ID\tCHANGELISTS\n";
      brokenby.write(header);
      fixedby.write(header);
      for (int testid : testids) {
        pt.advise(1);
        Status prevstatus = null;
        Set<Integer> already_recorded_changelists = new HashSet<>();
        int i;
        for (i=0; i<results.size(); i++) {
          TestSuiteRun tsr = results.get(i);
          Status status = tsr.getStatus(testid);
          if (status != null) {
            prevstatus = status;
            break;
          }
        }
        if (prevstatus == null) {
          // this test was never run
          continue;
        }
        List<Integer> unklist = new ArrayList<>();
        while (++i<results.size()) {
          TestSuiteRun tsr = results.get(i);
          Status status = tsr.getStatus(testid);
          if (status == null) {
            // test wasn't executed in this particular run so we don't know if the changelist
            // causing this run might have caused the test to switch polarity
            unklist.add(tsr.getRunid());
            continue;
          }
          if (prevstatus == status) {
            // the accumulated list of test suite runs probably didn't flip the test's polarity
            unklist.clear();
            continue;
          }
          unklist.add(tsr.getRunid());
          BufferedWriter out;
          switch (status) {
          case SUCCESS:
            out = fixedby;
            break;
          case FAILURE:
            out = brokenby;
            break;
          default:
            throw new AssertionError("" + status);
          }
          HashSet<Integer> changelists = Sets.newHashSet(Iterables.transform(unklist, new Function<Integer, Integer>() {
            @Override public Integer apply(Integer run_id) {
              return runs.getRunById(run_id).getChangelistId();
            }
          }));
          changelists.removeAll(already_recorded_changelists);
          if (!changelists.isEmpty()) {
            out.write(Integer.toString(testid));
            out.write('\t');
            out.write(Joiner.on("|").join(changelists));
            out.newLine();
            already_recorded_changelists.addAll(changelists);
          }
          unklist.clear();
          prevstatus = status;
        }
      }
    }
  }

  public static class TestResults {
    private final List<Integer> runids = new ArrayList<>();
    private final Map<Integer, Map<Byte, Set<Integer>>> map;

    public TestResults(Map<Integer, Map<Byte, Set<Integer>>> map) {
      this.map = map;
      runids.addAll(map.keySet());
    }

    public int size() {
      return map.size();
    }

    public TestSuiteRun get(int idx) {
      Integer runid = runids.get(idx);
      return new TestSuiteRun(runid, map.get(runid));
    }
  }

  public static class TestSuiteRun {
    private final Set<Integer> successes,failures,brokens;
    private final int runid;
    public TestSuiteRun(int runid, Map<Byte, Set<Integer>> map) {
      this.runid = runid;
      successes = notnull(map.get(MergeDataFilesMain.SUCCESS));
      brokens = notnull(map.get(MergeDataFilesMain.BROKEN));
      failures = notnull(map.get(MergeDataFilesMain.FAILURE));
    }

    private static final Set<Integer> notnull(Set<Integer> set) {
      return set == null ? Collections.<Integer>emptySet() : set;
    }

    public int getRunid() {
      return runid;
    }

    public Set<Integer> getSuccesses() {
      return successes;
    }

    public Set<Integer> getTests() {
      return Sets.union(getSuccesses(), getFailures());
    }

    public Set<Integer> getFailures() {
      return Sets.union(failures, brokens);
    }

    public int size() {
      return successes.size() + failures.size() + brokens.size();
    }

    public boolean wasSuccess(int testid) {
      return successes.contains(testid);
    }

    public boolean wasFailure(int testid) {
      return failures.contains(testid) || brokens.contains(testid);
    }

    public boolean wasSeen(int testid) {
      return successes.contains(testid) || failures.contains(testid) || brokens.contains(testid);
    }

    public Status getStatus(int testid) {
      return wasSuccess(testid) ? Status.SUCCESS
          : wasFailure(testid) ? Status.FAILURE : null;
    }

    public enum Status {
      SUCCESS, FAILURE
    }
  }
}
