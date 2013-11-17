package ron;

import io.LineReader;
import io.ProgressTracker;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import ron.GenerateBrokenByAndFixedBy.TestSuiteRun.Status;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import data.*;

/**
 * given {@code test_results.json} and {@code runs.txt} this utility produces output files: {@code pass.txt.gz},
 * {@code fixedby.txt}, and {@code brokenby.txt}.
 * Each line first gives a test id, followed by changelist_id's where the test passed, was fixed, or was broken.
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
        BufferedWriter pass = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(basedir + "pass.txt.gz"))));
        BufferedWriter brokenby = new BufferedWriter(new FileWriter(basedir + "brokenby.txt"));
        BufferedWriter fixedby = new BufferedWriter(new FileWriter(basedir + "fixedby.txt"))) {
      String header = "TEST_ID\tCHANGELISTS\n";
      brokenby.write(header);
      fixedby.write(header);
      pass.write(header);
      for (int testid : testids) {
        pt.advise(1);
        Status prevstatus = null;
        Set<Integer> already_recorded_changelists = new HashSet<>();
        TreeSet<Integer> passing_changelists = new TreeSet<>();

        // find earliest run of this test
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


        // holds a group of runs that may have caused a test to fail (or pass)
        List<Integer> uncertain_runs = new ArrayList<>();
        while (++i<results.size()) {
          TestSuiteRun tsr = results.get(i);
          Status status = tsr.getStatus(testid);
          if (status == null) {
            // test wasn't executed in this particular run so we don't know if the changelist
            // causing this run might have caused the test to switch polarity
            uncertain_runs.add(tsr.getRunid());
            continue;
          }
          if (prevstatus == status) {
            // the accumulated list of test suite runs probably didn't flip the test's polarity
            uncertain_runs.clear();

            if (status == Status.SUCCESS) {
              passing_changelists.add(runs.getRunById(tsr.getRunid()).getChangelistId());
            }
            continue;
          }
          uncertain_runs.add(tsr.getRunid());
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
          HashSet<Integer> changelists = Sets.newHashSet(Iterables.transform(uncertain_runs, new Function<Integer, Integer>() {
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
          uncertain_runs.clear();
          prevstatus = status;
        }
        pass.write(Integer.toString(testid));
        pass.write('\t');
        pass.write(Joiner.on("|").join(passing_changelists));
        pass.newLine();
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
