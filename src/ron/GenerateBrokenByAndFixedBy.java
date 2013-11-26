package ron;

import io.LineReader;
import io.ProgressTracker;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import ron.GenerateBrokenByAndFixedBy.TestSuiteRun.Status;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import data.*;
import data.Runs.FilterOnCreateDate;

/**
 * given {@code test_results.json} and {@code runs.txt} this utility produces output files: {@code pass.txt.gz},
 * {@code fixedby.txt}, and {@code brokenby.txt}.
 * Each line first gives a test id, followed by changelist_id's where the test passed, was fixed, or was broken.
 */
public class GenerateBrokenByAndFixedBy {
  //  ID      CREATE_DATE     TEST_DETAIL_ID  RUN_ID  TEST_STATUS
  //  9126993559      31-OCT-13       3233896 35011705        1
  public static void main(String[]args)throws Exception {
    String indir = "/Users/ry23/Dropbox/cmu-sfdc/ron_mallet/";
    String traindir = "/Users/ry23/Dropbox/cmu-sfdc/ron_mallet/train/";
    doit(indir, traindir, "01-JAN-80", "01-NOV-13");
    String testdir = "/Users/ry23/Dropbox/cmu-sfdc/ron_mallet/test/";
    doit(indir, testdir, "01-NOV-13", "01-NOV-14");
  }
  public static void doit(String indir, String outdir, String startdt, String enddt)throws Exception {
    if (!indir.endsWith("/")) throw new IllegalArgumentException(indir);
    if (!outdir.endsWith("/")) throw new IllegalArgumentException(outdir);

    SimpleDateFormat sdf = new SimpleDateFormat("dd-MMM-yy");
    long startmillis = sdf.parse(startdt).getTime();
    long endmillis = sdf.parse(enddt).getTime();

    final Runs runs = LineReader.handle(true, new Runs(new FilterOnCreateDate(startmillis, endmillis)), indir + "runs.txt");

    // run_id -> test_status -> test_detail_id
    TestResults results = new TestResults(PackUtils.readPackedJson(indir + "test_results.json"));
    results.retainAll(runs.getRunIds());

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
        BufferedWriter pass = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(outdir + "pass.txt.gz"))));
        BufferedWriter brokenby = new BufferedWriter(new FileWriter(outdir + "brokenby.txt"));
        BufferedWriter fixedby = new BufferedWriter(new FileWriter(outdir + "fixedby.txt"))) {
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

            if (status == Status.PASS) {
              passing_changelists.add(runs.getRunById(tsr.getRunid()).getChangelistId());
            }
            continue;
          }
          uncertain_runs.add(tsr.getRunid());
          BufferedWriter out;
          switch (status) {
          case PASS:
            out = fixedby;
            break;
          case FAIL:
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
    // map from runid to test status to test id's
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

    public void retainAll(Set<Integer> runids) {
      this.runids.retainAll(runids);
      map.keySet().retainAll(runids);
    }
  }

  public static class TestSuiteRun {
    private final Set<Integer> passes,fails,breaks;
    private final int runid;
    public TestSuiteRun(int runid, Map<Byte, Set<Integer>> map) {
      this.runid = runid;
      passes = notnull(map.get(MergeDataFilesMain.PASS));
      breaks = notnull(map.get(MergeDataFilesMain.BREAK));
      fails = notnull(map.get(MergeDataFilesMain.FAIL));
    }

    private static final Set<Integer> notnull(Set<Integer> set) {
      return set == null ? Collections.<Integer>emptySet() : set;
    }

    public int getRunid() {
      return runid;
    }

    public Set<Integer> getPasses() {
      return passes;
    }

    public Set<Integer> getTests() {
      return Sets.union(getPasses(), getFailures());
    }

    public Set<Integer> getFailures() {
      return Sets.union(fails, breaks);
    }

    public int size() {
      return passes.size() + fails.size() + breaks.size();
    }

    public boolean didPass(int testid) {
      return passes.contains(testid);
    }

    public boolean didFail(int testid) {
      return fails.contains(testid) || breaks.contains(testid);
    }

    public boolean wasSeen(int testid) {
      return passes.contains(testid) || fails.contains(testid) || breaks.contains(testid);
    }

    public Status getStatus(int testid) {
      return didPass(testid) ? Status.PASS
          : didFail(testid) ? Status.FAIL : null;
    }

    public enum Status {
      PASS, FAIL
    }
  }
}
