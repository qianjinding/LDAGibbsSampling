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
  private final Map<DocId, Doc> docs = new HashMap<>();

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
    public final DocId docid;
    public final int[] topicids;
    public final double[] topicweights;
    public Doc(String line) {
      String[] ar = line.split(" ");
      docid = new DocId(Integer.parseInt(ar[0]));
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
  public static final class DocId {
    public final int id;

    public DocId(int id) {
      this.id = id;
    }

    @Override public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + id;
      return result;
    }

    @Override public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      DocId other = (DocId) obj;
      if (id != other.id) return false;
      return true;
    }



  }
}