package data;

import java.util.HashSet;
import java.util.Set;

/**
 * Simple object holding at topic's id, its overall weight in the corpus, and the set of words
 * comprising the topic
 */
public final class Topic {
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