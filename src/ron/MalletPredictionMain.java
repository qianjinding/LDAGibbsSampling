package ron;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

public class MalletPredictionMain {
  static final class Topic {
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

  static class ChangelistSourceFiles {
    private final Map<String, Set<String>> changelist_id_to_files = new HashMap<>();
    private final Map<String, String> file_to_changelist_id = new HashMap<>();
    public String getChangelistId(String source_file) {
      return file_to_changelist_id.get(source_file);
    }
    public Set<String> getSourceFiles(String changelist_id) {
      return changelist_id_to_files.get(changelist_id);
    }
  }

  static class SourceFileTestFailures implements LineReader {
    private final Map<String, List<String>> source_file_to_failure_history = new HashMap<>();
    private final List<String> source_files = new ArrayList<>();
    /**
     * <pre>
     * docs.txt
     * longfilename<TAB>testid<TAB>testid
     * </pre>
     */
    @Override public void add(String line) {
      String[] ar = line.split("\t");
      source_file_to_failure_history.put(ar[0], new ArrayList<String>(Arrays.asList(ar).subList(1, ar.length)));
      source_files.add(ar[0]);
    }
    public String getSourceFile(int docid) {
      return source_files.get(docid);
    }
  }

  static class ChangelistTestFailures implements LineReader {
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
  static interface LineReader {
    void add(String line);
  }


  static class MalletPredictions implements LineReader {
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


  /**
   * map from topic id to test id's
   */
  static class Topics implements LineReader {
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

  public static void main(String[]args)throws Exception {
    // ID SEEN_DATE AFFECTED_FILES
    String changelists_tsv = "/Users/ry23/Dropbox/cmu-sfdc/data/changelists.txt";
    String mallet_output = "/Users/ry23/Downloads/malletorig/mallet-2.0.7/ron.output";
    String changelist_to_failures_filename = "changelist_to_failures_doc.txt";
    String topic_keys_filename = "/Users/ry23/Downloads/malletorig/mallet-2.0.7/ron.topickeys";
    String docs_txt = "/Users/ry23/Downloads/malletorig/mallet-2.0.7/docs.txt";

    ChangelistSourceFiles changelist_to_file_mapping = readChangelistToFileMapping(changelists_tsv);
    ChangelistTestFailures changelist_to_failures = handle(new ChangelistTestFailures(), changelist_to_failures_filename);
    Topics topics = handle(new Topics(), topic_keys_filename);
    SourceFileTestFailures source_file_to_failure_history = handle(new SourceFileTestFailures(), docs_txt);
    MalletPredictions docs = handle(new MalletPredictions(), mallet_output);

    {
      // evaluate performance for a single test
      List<Topic> relevant_topics = new ArrayList<>();
      String test_id = "15578983";
      for (Topic t : topics.topics()) {
        if (t.terms.contains(test_id)) {
          relevant_topics.add(t);
        }
      }

      List<Prediction> predictions = new ArrayList<>();

      List<Entry<String, Set<String>>> list = new ArrayList<>(changelist_to_failures.entries());
      // for every pair of changelists
      for (int i=0; i<list.size(); i++) {
        Entry<String, Set<String>> e = list.get(i);
        String changelist_id = e.getKey();
        Set<String> failures = e.getValue();
        for (int j=i+1; i<list.size(); i++) {

        }
      }

      for (Doc d : docs.docs()) {
        double score = 0;
        for (Topic t : relevant_topics) {
          for (int i=0; i<d.topicids.length; i++) {
            if (d.topicids[i] == t.topicid) {
              score += t.weight * d.topicweights[i];
            }
          }
        }
        String source_file = source_file_to_failure_history.getSourceFile(d.docid);
        String changelist_id = changelist_to_file_mapping.getChangelistId(source_file);
        Set<String> all_source_files = changelist_to_file_mapping.getSourceFiles(changelist_id);

        List<String> actual_failures = source_file_to_failure_history.source_file_to_failure_history.get(source_file);
        int count = 0;
        for (String s : actual_failures) {
          if (test_id.equals(s)) {
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

  }

  private static ChangelistSourceFiles readChangelistToFileMapping(String changelists_tsv) throws IOException {
    TsvParser cls = new TsvParser(changelists_tsv);
    ChangelistSourceFiles changelist_to_file_mapping = new ChangelistSourceFiles();
    for (String[] changelist: cls.rows) {
      for (String file : changelist[2].split("\n")) {
        Set<String> files = changelist_to_file_mapping.changelist_id_to_files.get(changelist[0]);
        if (files == null) {
          changelist_to_file_mapping.changelist_id_to_files.put(changelist[0], files = new HashSet<>());
        }
        files.add(file);
        changelist_to_file_mapping.file_to_changelist_id.put(file, changelist[0]);
      }
    }
    return changelist_to_file_mapping;
  }

  private static <T extends LineReader> T handle(T lineReader, String filename) throws IOException {
    try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
      String line = in.readLine(); // skip first line
      while (null != (line = in.readLine())) {
        lineReader.add(line);
     }
    }
    return lineReader;
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
