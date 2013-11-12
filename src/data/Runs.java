package data;

import io.LineReader;
import java.util.*;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * Represents the execution of a set of tests, possibly empty
 */
public class Runs extends LineReader {
  private final Map<Integer, Run> map = new HashMap<>();
  private final List<Run> list = new ArrayList<>();
  private final Multimap<String, Run> changelist_id_to_lines = HashMultimap.create();

  // ID CREATE_DATE STATUS CHANGELIST TYPE BUILD_FAILED
  // 1234 18-SEP-13 SKIP|FINISHED 54321 PARTIAL|FULL n|y
  @Override public void add(String line) {
    String[]ar = LineReader.split(line, '\t');
    int run_id = Integer.parseInt(ar[0]);
    String createDate = ar[1];
    String status = ar[2];
    String changelistid = ar[3];
    String type = ar[4];
    String build_failed = ar[5];

    Run run = new Run(run_id, createDate, status, changelistid, type, build_failed);
    map.put(run.runid, run);
    list.add(run);

    boolean added = changelist_id_to_lines.put(changelistid, run);
    if (!added) {
      throw new RuntimeException("curr: " + run);
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

  public Run getRunById(Integer run_id) {
    return map.get(run_id);
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
    public int getChangelistId() {
      return Integer.parseInt(changelistid);
    }
    @Override public String toString() {
      return "Run [runid=" + runid + ", createDate=" + createDate + ", status=" + status + ", changelistid="
          + changelistid + ", type=" + type + ", build_failed=" + build_failed + "]";
    }
    @Override public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((build_failed == null) ? 0 : build_failed.hashCode());
      result = prime * result + ((changelistid == null) ? 0 : changelistid.hashCode());
      result = prime * result + ((createDate == null) ? 0 : createDate.hashCode());
      result = prime * result + runid;
      result = prime * result + ((status == null) ? 0 : status.hashCode());
      result = prime * result + ((type == null) ? 0 : type.hashCode());
      return result;
    }
    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      Run other = (Run) obj;
      if (build_failed == null) {
        if (other.build_failed != null) return false;
      } else if (!build_failed.equals(other.build_failed)) return false;
      if (changelistid == null) {
        if (other.changelistid != null) return false;
      } else if (!changelistid.equals(other.changelistid)) return false;
      if (createDate == null) {
        if (other.createDate != null) return false;
      } else if (!createDate.equals(other.createDate)) return false;
      if (runid != other.runid) return false;
      if (status == null) {
        if (other.status != null) return false;
      } else if (!status.equals(other.status)) return false;
      if (type == null) {
        if (other.type != null) return false;
      } else if (!type.equals(other.type)) return false;
      return true;
    }
  }
}
