package data;

import io.LineReader;
import java.util.HashMap;
import java.util.Map;

/**
 * map from doc id to {@link data.Doc}
 */
public class Docs extends LineReader {
  /**
   * map from docid to {@link data.Doc}
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

  /**
   * a document is composed of topics and their weights
   */
  public static final class Doc {
    public final int docid;
    public final int[] topicids;
    public final double[] topicweights;
    public Doc(String line) {
      String[] ar = line.split(" ");
      docid = Integer.parseInt(ar[0]);
      if (!"null-source".equals(ar[1])) throw new RuntimeException(line);
      int n = ar.length/2 - 1;
      if (n * 2 + 2 != ar.length) throw new RuntimeException(n+" " + line);
      topicids = new int[n];
      topicweights = new double[n];
      for (int x = 0; x < n; x++) {
        topicids[x] = Integer.parseInt(ar[2 + x * 2]);
        topicweights[x] = Double.parseDouble(ar[2 + x * 2 + 1]);
      }
    }
  }

  public Doc getDoc(int docid) {
    return docs.get(docid);
  }
}