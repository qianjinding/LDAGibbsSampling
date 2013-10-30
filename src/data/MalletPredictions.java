package data;

import io.LineReader;
import java.util.HashMap;
import java.util.Map;

public class MalletPredictions extends LineReader {
  /**
   * map from docid to {@link Doc}
   */
  private final Map<Integer, Doc> docs = new HashMap<>();

  public Iterable<Doc> docs() {
    return docs.values();
  }

  /**
   * <pre>
   * ron.output
   * #doc source topic proportion ...
   * 0 null-source 25 0.6308917995984609 55 0.3519917627322033 96 0.014500541834220186 79 0.0010402955565968202
   * </pre>
   */
  @Override public void add(String line) {
    Doc d = new Doc(line);
    Doc prev = docs.put(d.docid, d);
    if (prev != null) throw new RuntimeException(line);
  }
}