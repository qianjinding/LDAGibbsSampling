package data;

import io.Tsv;
import java.io.IOException;
import java.util.*;

/**
 * map from autobuild run id to set of test id's that failed
 */
public class TestFailuresByRun {
  private final Map<Integer, Set<String>> test_failures_by_run_id = new HashMap<>();

  public static TestFailuresByRun readTestFailures(String test_failures_tsv) throws IOException {
    // ID CREATE_DATE TEST_DETAIL_ID RUN_ID
    // 80001 14-SEP-13 12345 1234
    // 80002 14-SEP-13 12341 1234
    Tsv tfs = new Tsv(test_failures_tsv);

    // build map from run id to test failures
    TestFailuresByRun test_failures_by_run_id = new TestFailuresByRun();
    for (String[] tf : tfs.rows()) {
      Set<String> prev = test_failures_by_run_id.test_failures_by_run_id.get(tf[3]);
      if (prev == null) {
        test_failures_by_run_id.test_failures_by_run_id.put(Integer.parseInt(tf[3]), prev = new HashSet<String>());
      }
      prev.add(tf[2]);
    }
    return test_failures_by_run_id;
  }

  public Set<String> getTestFailures(int run_id) {
    return test_failures_by_run_id.get(run_id);
  }
}