package data;

import io.LineReader;
import java.util.*;

/**
 * map from topic id to {@link data.Topic}
 */
public class Topics extends LineReader {
  private final Map<Integer, Topic> topics = new HashMap<>();

  /**
   * <pre>
   * ron.topickeys
   * 6       0.00868 14669524 15841851 15841684 14669525 15841290 14669526 14669523 15841555 14669527 15841611 15841413 15841284 3230927
   * </pre>
   */
  @Override public void add(String line) {
    Topic t = new Topic(line);
    Topic prev = topics.put(t.topicid, t);
    if (prev != null) throw new RuntimeException(line);
  }

  public Iterable<Topic>topics() {
    return topics.values();
  }


  /**
   * Simple object holding at topic's id, its overall weight in the corpus, and the set of words
   * comprising the topic
   */
  public static final class Topic {
    public final int topicid;
    public final double weight;
    public final Set<String> terms;
    Topic(String s) {
      String[]ar = s.split("\t");
      topicid = Integer.parseInt(ar[0]);
      String[] ar2 = ar[2].split(" ");
      weight = Double.parseDouble(ar[1]);
      terms = new HashSet<>();
      for (int i=1; i<ar2.length; i++) {
        terms.add(ar2[i]);
      }
    }
  }
}