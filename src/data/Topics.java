package data;

import io.LineReader;
import java.util.HashMap;
import java.util.Map;

/**
 * map from topic id to test id's
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
}