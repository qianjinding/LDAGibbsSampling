package data;

import java.util.HashSet;
import java.util.Set;

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