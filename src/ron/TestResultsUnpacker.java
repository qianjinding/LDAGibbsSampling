package ron;

import io.ProgressTracker;
import java.util.*;
import ron.TestResultsUnpacker.TestSuiteRun.Status;
import com.google.common.collect.Sets;
import data.PackUtils;

public class TestResultsUnpacker {
  //  ID      CREATE_DATE     TEST_DETAIL_ID  RUN_ID  TEST_STATUS
  //  9126993559      31-OCT-13       3233896 35011705        1
  public static void main(String[]args)throws Exception {
    // run_id -> test_status -> test_detail_id
    String basedir = "/Users/ry23/Dropbox/cmu-sfdc/ron_mallet/";
    TestResults results = new TestResults(PackUtils.readPackedJson(basedir + "test_results.json"));
    System.out.println(results.size());

    Set<Integer> testids = new HashSet<>();

    try (ProgressTracker pt = new ProgressTracker(null, "index", results.size(), "runs", "tests")) {
      for (int i=0; i<results.size(); i++) {
        TestSuiteRun tsr = results.get(i);
        for (int testid : tsr.getTests()) {
          testids.add(testid);
        }
        pt.advise(1, tsr.size());
      }
    }

    for (int testid : testids) {
      Status prevstatus = null;
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
        System.out.println(testid+" " + status+" " + unklist);
        unklist.clear();
        prevstatus = status;
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
      successes = notnull(map.get(MergeDataFiles.SUCCESS));
      brokens = notnull(map.get(MergeDataFiles.BROKEN));
      failures = notnull(map.get(MergeDataFiles.FAILURE));
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
