package ron;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

public class FartMain {
  static final class Topic {
    public final int topicid;
    public final double d;
    public final Set<String> terms;
    Topic(String s) {
      String[]ar = s.split("\t");
      topicid = Integer.parseInt(ar[0]);
      String[] ar2 = ar[2].split(" ");
      d = Double.parseDouble(ar[1]);
      terms = new HashSet<>();
      for (int i=1; i<ar2.length; i++) {
        terms.add(ar2[i]);
      }
    }
  }
  static final class Doc {
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
  public static void main(String[]args)throws Exception {
    // ron.topickeys
    // 6       0.00868 14669524 15841851 15841684 14669525 15841290 14669526 14669523 15841555 14669527 15841611 15841413 15841284 3230927
    Map<Integer, Topic> topics = new HashMap<>();
    try (BufferedReader in = new BufferedReader(new FileReader("/Users/ry23/Downloads/malletorig/mallet-2.0.7/ron.topickeys"))) {
      String line;
      while (null != (line = in.readLine())) {
        Topic t = new Topic(line);
        Topic prev = topics.put(t.topicid, t);
        if (prev != null) throw new RuntimeException(line);
      }
    }

    // docs.txt
    // longfilename<TAB>testid<TAB>testid
    Map<String, List<String>> source_file_to_failure_history = new HashMap<>();
    List<String> source_files = new ArrayList<>();
    try (BufferedReader in = new BufferedReader(new FileReader("/Users/ry23/Downloads/malletorig/mallet-2.0.7/docs.txt"))) {
      String line;
      while (null != (line = in.readLine())) {
        String[] ar = line.split("\t");
        source_file_to_failure_history.put(ar[0], new ArrayList<String>(Arrays.asList(ar).subList(1, ar.length)));
        source_files.add(ar[0]);
      }
    }

    // ron.output
    // #doc source topic proportion ...
    // 0 null-source 25 0.6308917995984609 55 0.3519917627322033 96 0.014500541834220186 79 0.0010402955565968202
    Map<Integer, Doc> docs = new HashMap<>();
    try (BufferedReader in = new BufferedReader(new FileReader("/Users/ry23/Downloads/malletorig/mallet-2.0.7/ron.output"))) {
      String line = in.readLine(); // skip first line
      while (null != (line = in.readLine())) {
        Doc d = new Doc(line);
        Doc prev = docs.put(d.docid, d);
        if (prev != null) throw new RuntimeException(line);
      }
    }

    {
      // evaluate performance for a single test
      List<Topic> relevant_topics = new ArrayList<>();
      // 15578983
      for (Topic t : topics.values()) {
        if (t.terms.contains("15578983")) {
          relevant_topics.add(t);
        }
      }

      List<Prediction> predictions = new ArrayList<>();

      for (Doc d : docs.values()) {
        double score = 0;
        for (Topic t : relevant_topics) {
          for (int i=0; i<d.topicids.length; i++) {
            if (d.topicids[i] == t.topicid) {
              score += t.d * d.topicweights[i];
            }
          }
        }
        String source_file = source_files.get(d.docid);
        List<String> actual_failures = source_file_to_failure_history.get(source_file);
        int count = 0;
        for (String s : actual_failures) {
          if ("15578983".equals(s)) {
            count++;
          }
        }
        predictions.add(new Prediction(d.docid, score, count, source_file));
      }

      int total = 0;
      int numcorrect = 0;
      Random r = new Random();
      while (total < 100000) {
        int a = r.nextInt(predictions.size());
        int b = r.nextInt(predictions.size());
        Prediction p1 = predictions.get(a);
        Prediction p2 = predictions.get(b);
        if ((p1.actual_failure_count == 0) == (p2.actual_failure_count == 0)) {
          continue;
        }
        total++;
        boolean correct = p1.score > p2.score == p1.actual_failure_count > p2.actual_failure_count;
        System.out.println(correct+" " + p1.score+" " + p1.actual_failure_count+" " + p2.score +" " + p2.actual_failure_count);
        if (correct) numcorrect++;
      }
      System.out.println(numcorrect +" correct, out of " + total);
    }

    // ID SEEN_DATE AFFECTED_FILES
    CrapParser cls = new CrapParser("/Users/ry23/Dropbox/cmu-sfdc/data/changelists.txt");

  }

  static class Prediction {
    public final int docid;
    public final double score;
    public final int actual_failure_count;
    public final String source_file;
    public Prediction(int docid, double score, int actual_failure_count, String source_file) {
      this.docid = docid;
      this.score = score;
      this.actual_failure_count = actual_failure_count;
      this.source_file = source_file;
    }

  }
}
