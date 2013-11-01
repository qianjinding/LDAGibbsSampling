package data;

import io.LineReader;
import java.util.*;

/**
 * Represents the execution of a set of tests, possibly empty
 */
public class Runs extends LineReader {
  private final Map<Integer, Run> map = new HashMap<>();
  private final List<Run> list = new ArrayList<>();

  // ID CREATE_DATE STATUS CHANGELIST TYPE BUILD_FAILED
  // 1234 18-SEP-13 SKIP|FINISHED 54321 PARTIAL|FULL n|y
  @Override public void add(String line) {
    String[]ar = line.split("\t");
    Run run = new Run(Integer.parseInt(ar[0]), ar[1], ar[2], ar[3], ar[4], ar[5]);
    map.put(run.runid, run);
    list.add(run);
  }

  public static class Run {
    public final int runid;
    public final String createDate;
    public final String status;
    public final String changelistid;
    public final String type;
    public final String build_failed;
    public Run(int runid, String createDate, String status, String changelistid, String type,
        String build_failed) {
      this.runid = runid;
      this.createDate = createDate;
      this.status = status;
      this.changelistid = changelistid;
      this.type = type;
      this.build_failed = build_failed;
    }

  }

  public int size() {
    return map.size();
  }

  public Run getRunAtIndex(int i) {
    return list.get(i);
  }

  public Iterable<Run> runs() {
    return map.values();
  }
}
