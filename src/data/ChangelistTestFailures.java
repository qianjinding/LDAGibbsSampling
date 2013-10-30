package data;

import io.LineReader;
import java.util.*;
import java.util.Map.Entry;

public class ChangelistTestFailures extends LineReader {
  /**
   * map from changelist id to unique test id's
   */
  private final Map<String, Set<String>> changelist_to_failures = new HashMap<>();

  @Override public void add(String line) {
    String[] ar = line.split("\t");
    Set<String> set = new HashSet<>();
    Set<String> prev = changelist_to_failures.put(ar[0], set);
    if (prev != null) throw new RuntimeException(line);
    for (int i=1; i<ar.length; i++) {
      boolean added = set.add(ar[i]);
      if (!added) throw new RuntimeException(line);
    }
  }

  public Set<Entry<String, Set<String>>> entries() {
    return changelist_to_failures.entrySet();
  }
}